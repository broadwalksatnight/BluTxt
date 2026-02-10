using System;
using System.Linq;
using System.Threading.Tasks;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Services.Maps;
using Windows.Storage.Streams;

namespace BluTxt
{
    public class BleManager
    {
        private BluetoothLEAdvertisementWatcher _watcher;

        public event Action<BleDeviceInfo> DeviceFound;
        public event Action<byte[]> NotificationReceived;

        private GattCharacteristic _txCharacteristic;
        private GattCharacteristic _rxCharacteristic;
        private GattCharacteristic _terminateCharacteristic;

        private readonly Guid myService = Guid.Parse("00002222-0000-1000-8000-00805F9B34FB");
        private readonly Guid txCharacteristic = Guid.Parse("F9D1737F-65F8-5FE9-8025-0AD67E260AAF");
        private readonly Guid rxCharacteristic = Guid.Parse("F9D1737F-65F8-5FE9-8025-0AD67E260AAD");
        private readonly Guid terminateCharacteristic = Guid.Parse("F9D1737F-65F8-5FE9-8025-0AD67E260ABA");


        public class BleDeviceInfo
        {
            public ulong BluetoothAddress { get; set; }
            public string Name { get; set; }
        }


        // --------------------------
        // Scan for devices
        // --------------------------
        public void StartScan(Guid serviceUuid)
        {
            _watcher = new BluetoothLEAdvertisementWatcher
            {
                ScanningMode = BluetoothLEScanningMode.Active
            };

            _watcher.Received += (sender, args) =>
            {
                if (args.Advertisement.ServiceUuids.Contains(serviceUuid))
                {
                    DeviceFound?.Invoke(new BleDeviceInfo
                    {
                        BluetoothAddress = args.BluetoothAddress,
                        Name = string.IsNullOrWhiteSpace(args.Advertisement.LocalName) ? "(unknown)" : args.Advertisement.LocalName
                    });
                }
            };



            _watcher.Start();
        }

        // --------------------------
        // Connect & setup characteristics
        // --------------------------
        public async Task ConnectDeviceAsync(ulong address)
        {
            var device = await BluetoothLEDevice.FromBluetoothAddressAsync(address);
            var servicesResult = await device.GetGattServicesAsync();
            var myServiceObj = servicesResult.Services.FirstOrDefault(s => s.Uuid == myService);
            if (myServiceObj == null) return;

            var charsResult = await myServiceObj.GetCharacteristicsAsync();
            foreach (var characteristic in charsResult.Characteristics)
            {
                string uuid = characteristic.Uuid.ToString().ToUpper();

                if (uuid == txCharacteristic.ToString().ToUpper())
                {
                    _txCharacteristic = characteristic;
                    characteristic.ValueChanged += Characteristic_ValueChanged;
                    await characteristic.WriteClientCharacteristicConfigurationDescriptorAsync(
                        GattClientCharacteristicConfigurationDescriptorValue.Notify);
                    Console.WriteLine("TX characteristic found (Notify)");
                }
                else if (uuid == rxCharacteristic.ToString().ToUpper())
                {
                    _rxCharacteristic = characteristic;
                    Console.WriteLine("RX characteristic found (Write)");
                }
                else if (uuid == terminateCharacteristic.ToString().ToUpper())
                {
                    _terminateCharacteristic = characteristic;
                    characteristic.ValueChanged += Characteristic_ValueChanged;
                    await characteristic.WriteClientCharacteristicConfigurationDescriptorAsync(
                        GattClientCharacteristicConfigurationDescriptorValue.Notify);
                    Console.WriteLine("Terminate characteristic found (Notify)");
                }
            }
        }


        private void Characteristic_ValueChanged(GattCharacteristic sender, GattValueChangedEventArgs args)
        {
            var reader = DataReader.FromBuffer(args.CharacteristicValue);
            byte[] data = new byte[args.CharacteristicValue.Length];
            reader.ReadBytes(data);

            NotificationReceived?.Invoke(data);
        }

        // Optional: write data to RX characteristic
        public async Task WriteToRxAsync(byte[] data)
        {
            if (_rxCharacteristic == null) return;

            var writer = new DataWriter();
            writer.WriteBytes(data);

            await _rxCharacteristic.WriteValueAsync(writer.DetachBuffer(),
                GattWriteOption.WriteWithoutResponse);
        }
    }
}
