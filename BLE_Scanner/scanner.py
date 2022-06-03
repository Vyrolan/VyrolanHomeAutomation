from bleak import BleakScanner
import paho.mqtt.client as mqtt
import asyncio
import binascii
import click
import concurrent.futures
import json
import threading
import time


class Beacon(object):
    def __init__(self, beacon, ad_data):
        self.beacon = beacon
        self.ad_data = ad_data
        self.first_seen = time.time()
        self.last_seen = time.time()
        self.uuid = None
        self.major = None
        self.minor = None
        self.name = beacon.name
        self.rssi = beacon.rssi
        self.address = beacon.address
        self.set_ibeacon_values()

    def bump(self, beacon, ad_data):
        self.last_seen = time.time()
        self.beacon = beacon
        self.ad_data = ad_data
        new_uuid = self.set_ibeacon_values()
        new_rssi = False
        if abs(beacon.rssi - self.rssi) >= 6:
            self.rssi = beacon.rssi
            new_rssi = True
        return new_uuid, new_rssi

    @property
    def age(self):
        return time.time() - self.last_seen

    @property
    def expired(self):
        # Checking for first seen a minute ago to not inadvertently
        # get a false presence when first showing up and switching
        # from triggered to non-triggered.
        #
        # Example: Vehicle pulls up in triggered (motion) state sending
        # adverts rapidly...vehicle stops and switches to non-triggered
        # to send adverts slowly...the slow ones may not get seen within
        # the shorter triggered threshold of 15 seconds.  In reverse, the
        # vehicle has been seen for a long time and then leaves, the first
        # seen check easily passes and we then want the fast threshold.
        if self.triggered and time.time() - self.first_seen >= 60:
            return self.age > 15
        return self.age > 120

    @property
    def triggered(self):
        return self.uuid.endswith('1')

    def set_ibeacon_values(self):
        try:
            mfg_data = self.beacon.details['props']['ManufacturerData'][76]
        except KeyError:
            # noinspection PyBroadException
            try:
                mfg_data = self.ad_data.manufacturer_data['76']
            except Exception:
                return False

        mfg_data = binascii.hexlify(mfg_data).decode()

        new_uuid = False
        uuid = mfg_data[4:36].upper()
        uuid = f"{uuid[0:8]}-{uuid[8:12]}-{uuid[12:16]}-{uuid[16:20]}-{uuid[20:]}"
        if uuid != self.uuid:
            self.uuid = uuid
            new_uuid = True

        self.major = int(mfg_data[36:40], 16)
        self.minor = int(mfg_data[40:44], 16)

        return new_uuid

    def __str__(self):
        return f"{self.name}: UUID={self.uuid}, Major={self.major}, Minor={self.minor}, " \
               f"RSSI={self.rssi}, MAC={self.address}"

    def payload(self, presence):
        return json.dumps({
            "name": self.name,
            "uuid": self.uuid,
            "major": self.major,
            "minor": self.minor,
            "rssi": self.rssi,
            "address": self.address,
            "presence": presence,
            "triggered": self.triggered,
            "updated": self.last_seen
        })

    def __hash__(self):
        return hash(self.address)

    def __eq__(self, other):
        return self.address == other.address

    def __ne__(self, other):
        return self.address != other.address


class BLEScanner(object):
    def __init__(self):
        self.scanner = BleakScanner()

    def add_callback(self, func):
        self.scanner.register_detection_callback(func)

    async def start(self):
        await self.scanner.start()

    async def stop(self):
        await self.scanner.stop()

    async def scan(self, duration=5):
        await self.scanner.start()
        await asyncio.sleep(duration)
        await self.scanner.stop()

        beacons = self.scanner.discovered_devices
        return beacons


class MQTTPublisher(object):
    def __init__(self, host, port, username, password):
        self.host = host
        self.port = port
        self.client = mqtt.Client("Beacon2MQTT")
        self.client.username_pw_set(username, password)
        self.client.on_log = self.on_mqtt_log
        self.client.on_disconnect = self.on_mqtt_disconnect
        self._connected = False

    def on_mqtt_disconnect(self, client, userdata, rc):
        print(f"DISCONNECT: Client={client}, UserData={userdata}, ReasonCode={rc}\n")
        self._connected = False

    @staticmethod
    def on_mqtt_log(client, userdata, level, buf):
        print(f"LOG: Client={client}, UserData={userdata}, Level={level}, Buffer={buf}\n")

    def connect(self):
        if self._connected:
            return
        self.client.connect(self.host, self.port)
        self._connected = True
        self.client.loop_start()
        
    def publish(self, topic, payload=None, qos=0, retain=False):
        if not self._connected:
            self.connect()
        self.client.publish(topic, payload, qos, retain)


class Beacon2MQTT(object):
    def __init__(self, host, port, username, password):
        self.scanner = BLEScanner()
        self.scanner.add_callback(self.detection_callback)
        self.mqtt = MQTTPublisher(host, port, username, password)
        self.beacons = {}
        self.lock = threading.Lock()
        self.lock_pool = concurrent.futures.ThreadPoolExecutor()
        self.loop = asyncio.get_event_loop()

    def detection_callback(self, device, advertisement_data):
        if not device.name.startswith('Vyro'):
            return

        send_mqtt = True
        if True:  # with self.lock:
            if device.address in self.beacons:
                beacon = self.beacons[device.address]
                new_uuid, new_rssi = beacon.bump(device, advertisement_data)
                if new_uuid:
                    print(f"Redetected beacon {device.name} with new UUID={beacon.uuid}")
                elif new_rssi:
                    print(f"Redetected beacon {device.name} with new RSSI={device.rssi}")
                else:
                    print(f"Redetected beacon {device.name}")
                    send_mqtt = False
            else:
                print(f"Detected new beacon {device.name} with RSSI={device.rssi}")
                beacon = Beacon(device, advertisement_data)
                self.beacons[device.address] = beacon

        if send_mqtt:
            self.mqtt.publish(topic=f"beacons/presence/{beacon.name[4:]}", payload=beacon.payload(True),
                              qos=1, retain=True)

    async def check_expired(self):
        # await self.loop.run_in_executor(self.lock_pool, self.lock.acquire)
        if True:  # try:
            expired = []
            for address, beacon in self.beacons.items():
                if beacon.triggered:
                    print(f"{beacon.name} TRIGGERED and last seen {beacon.age:.3f} seconds ago")
                else:
                    print(f"{beacon.name} last seen {beacon.age:.3f} seconds ago")
                if beacon.expired:
                    expired.append((address, beacon))
            for address, beacon in expired:
                print(f"Expired {beacon.name}")
                self.mqtt.publish(topic=f"beacons/presence/{beacon.name[4:]}", payload=beacon.payload(False),
                                  qos=1, retain=True)
                del self.beacons[address]
        # finally:
        #     self.lock.release()

    async def run(self):
        while True:
            print("Starting scan...")
            await self.scanner.start()
            await asyncio.sleep(6)
            print("Stopping scan...")
            await self.scanner.stop()
            print("Checking for expired beacons...")
            await self.check_expired()


async def async_main(host, port, username, password):
    b2m = Beacon2MQTT(host, port, username, password)
    await b2m.run()


@click.command()
@click.option('-h', '--host', default='localhost', help='MQTT Hostname')
@click.option('-p', '--port', type=int, default=1883, help='MQTT Port')
@click.option('-u', '--username', required=True, help='MQTT Username')
@click.option('-w', '--password', required=True, help='MQTT Password')
def main(host, port, username, password):
    asyncio.run(async_main(host, port, username, password))


if __name__ == "__main__":
    main()
