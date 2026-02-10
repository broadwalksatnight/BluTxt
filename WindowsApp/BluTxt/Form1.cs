using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Threading.Tasks;
using System.Windows.Forms;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Storage.Streams;

namespace BluTxt
{
    public partial class Form1 : Form
    {
        // -----------------------------
        // BLE fields
        // -----------------------------
        private BluetoothLEAdvertisementWatcher _watcher;
        private Dictionary<ulong, string> _devices = new();
        private BluetoothLEDevice _connectedDevice;
        private GattCharacteristic _txCharacteristic;
        private GattCharacteristic _rxCharacteristic;
        private GattCharacteristic _terminateCharacteristic;

        // -----------------------------
        // Inline UUIDs
        // -----------------------------
        private readonly Guid serviceUuid = Guid.Parse("00002222-0000-1000-8000-00805F9B34FB");
        private readonly Guid txCharacteristicUuid = Guid.Parse("F9D1737F-65F8-5FE9-8025-0AD67E260AAF");
        private readonly Guid rxCharacteristicUuid = Guid.Parse("F9D1737F-65F8-5FE9-8025-0AD67E260AAD");
        private readonly Guid terminateCharacteristicUuid = Guid.Parse("F9D1737F-65F8-5FE9-8025-0AD67E260ABA");

        public Form1()
        {
            InitializeComponent();
        }

        // -----------------------------
        // Scan for BLE devices
        // -----------------------------
        private void btnScan_Click(object sender, EventArgs e)
        {
            listBoxDevices.Items.Clear();
            _devices.Clear();

            _watcher = new BluetoothLEAdvertisementWatcher
            {
                ScanningMode = BluetoothLEScanningMode.Active
            };

            _watcher.Received += Watcher_Received;
            _watcher.Start();

            lblStatus.Text = "Scanning...";
        }

        private void Watcher_Received(BluetoothLEAdvertisementWatcher sender,
                               BluetoothLEAdvertisementReceivedEventArgs args)
        {
            string name = string.IsNullOrWhiteSpace(args.Advertisement.LocalName) ? "(unknown)" : args.Advertisement.LocalName;

            // Always print to console
            Console.WriteLine($"Advertisement: {name} | {args.BluetoothAddress:X}");

            // Add to listbox safely on UI thread
            if (!_devices.ContainsKey(args.BluetoothAddress))
            {
                _devices[args.BluetoothAddress] = name;
                BeginInvoke(() =>
                {
                    listBoxDevices.Items.Add($"{name} | {args.BluetoothAddress:X}");
                });
            }
        }


        // -----------------------------
        // Connect to selected device
        // -----------------------------

        private async void listBoxDevices_MouseDoubleClick(object sender, MouseEventArgs e)
        {
            if (listBoxDevices.SelectedItem == null)
                return;

            string selected = listBoxDevices.SelectedItem.ToString();
            ulong address = Convert.ToUInt64(selected.Split('|')[1].Trim(), 16);

            lblStatus.Text = "Connecting...";
            _connectedDevice = await BluetoothLEDevice.FromBluetoothAddressAsync(address);

            if (_connectedDevice == null)
            {
                lblStatus.Text = "Connection failed";
                return;
            }

            lblStatus.Text = $"Connected to {_connectedDevice.Name}";
            await SetupCharacteristicsAsync();
        }





        // -----------------------------
        // Discover characteristics & subscribe
        // -----------------------------
        private async System.Threading.Tasks.Task SetupCharacteristicsAsync()
        {
            var servicesResult = await _connectedDevice.GetGattServicesAsync();
            var service = servicesResult.Services.FirstOrDefault(s => s.Uuid == serviceUuid);

            if (service == null)
            {
                Log("Service not found");
                return;
            }

            var charResult = await service.GetCharacteristicsAsync();

            // TX (Notify)
            _txCharacteristic = charResult.Characteristics.FirstOrDefault(c => c.Uuid == txCharacteristicUuid);
            if (_txCharacteristic != null)
            {
                _txCharacteristic.ValueChanged += Notify_ValueChanged;
                await _txCharacteristic.WriteClientCharacteristicConfigurationDescriptorAsync(
                    GattClientCharacteristicConfigurationDescriptorValue.Notify);
                Log("TX notifications enabled");
            }

            // RX (Write)
            _rxCharacteristic = charResult.Characteristics.FirstOrDefault(c => c.Uuid == rxCharacteristicUuid);
            if (_rxCharacteristic != null)
            {
                Log("RX characteristic found (can write)");
            }

            // Terminate (Notify)
            _terminateCharacteristic = charResult.Characteristics.FirstOrDefault(c => c.Uuid == terminateCharacteristicUuid);
            if (_terminateCharacteristic != null)
            {
                _terminateCharacteristic.ValueChanged += Notify_ValueChanged;
                await _terminateCharacteristic.WriteClientCharacteristicConfigurationDescriptorAsync(
                    GattClientCharacteristicConfigurationDescriptorValue.Notify);
                Log("Terminate notifications enabled");
            }
        }

        // -----------------------------
        // Notification handler
        // -----------------------------
        private void Notify_ValueChanged(GattCharacteristic sender, GattValueChangedEventArgs args)
        {
            var reader = DataReader.FromBuffer(args.CharacteristicValue);
            byte[] data = new byte[args.CharacteristicValue.Length];
            reader.ReadBytes(data);

            string text = System.Text.Encoding.UTF8.GetString(data);

            BeginInvoke(() =>
            {
                LogMessage(text.Trim(), false);
            });
        }



        // -----------------------------
        // Optional: write to RX
        // -----------------------------
        private async System.Threading.Tasks.Task WriteToRxAsync(byte[] data)
        {
            if (_rxCharacteristic == null) return;

            var writer = new DataWriter();
            writer.WriteBytes(data);

            await _rxCharacteristic.WriteValueAsync(writer.DetachBuffer(), GattWriteOption.WriteWithoutResponse);
        }

        // -----------------------------
        // Logging helper
        // -----------------------------
        private void Log(string message)
        {
            textBoxLog.AppendText($"[{DateTime.Now:HH:mm:ss}] {message}{Environment.NewLine}");
        }


        // -----------------------------
        // Textbox Logic
        // -----------------------------

        private async void textBoxSend_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.KeyCode != Keys.Enter)
                return;

            e.SuppressKeyPress = true;
            await SendMessageAsync();

            if (_rxCharacteristic == null)
                return;

            string message = textBoxSend.Text;
            if (string.IsNullOrWhiteSpace(message))
                return;

            textBoxSend.Clear();

            var writer = new Windows.Storage.Streams.DataWriter();
            writer.WriteBytes(System.Text.Encoding.UTF8.GetBytes(message));

            await _rxCharacteristic.WriteValueAsync(
                writer.DetachBuffer(),
                GattWriteOption.WriteWithResponse
            );

            Log("Sent: " + message);
        }

        private void LogMessage(string message, bool isTx)
        {
       
            textBoxChat.SelectionStart = textBoxChat.TextLength;
            textBoxChat.SelectionLength = 0;

            textBoxChat.SelectionAlignment = isTx ? HorizontalAlignment.Left : HorizontalAlignment.Right;
           
            textBoxChat.SelectionColor = isTx ? Color.Blue : Color.Green;

            string prefix = isTx ? "" : "";
            textBoxChat.AppendText($"({DateTime.Now:HH:mm}) {prefix}{message}{Environment.NewLine}");

            textBoxChat.SelectionColor = textBoxChat.ForeColor;
            textBoxChat.SelectionAlignment = HorizontalAlignment.Left;
            textBoxChat.ScrollToCaret();
        }




        private async Task SendMessageAsync()
        {
            if (_rxCharacteristic == null) return;

            string message = textBoxSend.Text;
            if (string.IsNullOrWhiteSpace(message)) return;

            textBoxSend.Clear();

            byte[] data = System.Text.Encoding.UTF8.GetBytes(message);
            var writer = new DataWriter();
            writer.WriteBytes(data);

            await _rxCharacteristic.WriteValueAsync(writer.DetachBuffer(), GattWriteOption.WriteWithResponse);

            
            LogMessage(message, true);
        }







    }
}
