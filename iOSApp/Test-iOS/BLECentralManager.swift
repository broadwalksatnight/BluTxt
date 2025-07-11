//
//  BLECentralManager 2.swift
//  Test-iOS
//
//  Created by Jack McCaffrey on 12/6/25.
//


import CoreBluetooth
import Foundation
import UserNotifications
import UIKit



// MARK: - Notification Extension

extension Notification.Name {
    static let didDisconnectFromPeripheral = Notification.Name("didDisconnectFromPeripheral")
}

extension Notification.Name {
    static let didConnectToPeripheral = Notification.Name("didConnectToPeripheral")
}



var incomingDataBuffer = Data()



struct DiscoveredDevice: Identifiable, Equatable {
    let id = UUID()
    let peripheral: CBPeripheral
    let name: String
    
    static func == (lhs: DiscoveredDevice, rhs: DiscoveredDevice) -> Bool {
        return lhs.peripheral.identifier == rhs.peripheral.identifier
    }
}

class BLECentralManager: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    @Published var discoveredDevices: [DiscoveredDevice] = []
    @Published var connectedPeripheral: CBPeripheral?
    @Published var txCharacteristic: CBCharacteristic?
    @Published var rxCharacteristic: CBCharacteristic?
    @Published var terminateCharacteristic: CBCharacteristic?
    @Published var isDisconnectedByRemote = false
    @Published var centralManagerState: CBManagerState = .unknown
    @Published var connectingPeripheral: CBPeripheral?

    

    
    
    private var centralManager: CBCentralManager!
    private var dataModel: BluetoothDataModel
    private var wasDisconnected = false
    
    init(dataModel: BluetoothDataModel) {
        self.dataModel = dataModel
        super.init()
        print("Initializing BLECentralManager... Thread: \(Thread.current)")
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    
    var isConnected: Bool {
        return connectedPeripheral?.state == .connected
    }

    
    // MARK: - CBCentralManagerDelegate
    
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        print("Central manager state changed to: \(central.state.rawValue)")
        centralManagerState = central.state
        if central.state == .poweredOn {
            print("Central powered on. Starting scan...")
            centralManager.scanForPeripherals(withServices: [CBUUID(string: "2222")], options: nil)
        }
    }

    func startScan() {
        centralManager.scanForPeripherals(withServices: [CBUUID(string: "2222")], options: nil)
        print("🔍 Started scanning for peripherals")
    }

    
    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String : Any],
                        rssi RSSI: NSNumber) {

        let advertisedName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
            ?? peripheral.name
            ?? "Unknown"

        let newDevice = DiscoveredDevice(peripheral: peripheral, name: advertisedName)

        if let index = discoveredDevices.firstIndex(where: { $0.peripheral.identifier == peripheral.identifier }) {
            if discoveredDevices[index].name != advertisedName {
                discoveredDevices[index] = newDevice
                print("Updated name to: \(advertisedName) (RSSI: \(RSSI))")
            }
        } else {
            discoveredDevices.append(newDevice)
            print("Discovered: \(advertisedName) (RSSI: \(RSSI))")
        }

        let rssiValue = RSSI.intValue
        if rssiValue > -65 {
            notifyProximity(of: advertisedName, rssi: rssiValue)
            
        }

    }
    
    private var recentlyNotifiedDevices: Set<UUID> = []

    private func notifyProximity(of name: String, rssi: Int) {
        guard let peripheral = discoveredDevices.first(where: { $0.name == name })?.peripheral else { return }

        if recentlyNotifiedDevices.contains(peripheral.identifier) {
            return
        }

        recentlyNotifiedDevices.insert(peripheral.identifier)

        let content = UNMutableNotificationContent()
        content.title = "Nearby User"
        content.body = "\(name) is nearby"
        content.sound = .default
        

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )

        UNUserNotificationCenter.current().add(request)

        DispatchQueue.main.asyncAfter(deadline: .now() + 30) { [weak self] in
            self?.recentlyNotifiedDevices.remove(peripheral.identifier)
        }
    }



    func connect(to device: DiscoveredDevice) {
        centralManager.stopScan()
        connectingPeripheral = device.peripheral
        device.peripheral.delegate = self
        centralManager.connect(device.peripheral, options: nil)
    }

    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        print("Connected to \(peripheral.name ?? "unknown")")
        connectedPeripheral = peripheral
        connectingPeripheral = nil
        peripheral.discoverServices([CBUUID(string: "2222")])
        
        NotificationCenter.default.post(name: .didConnectToPeripheral, object: peripheral)
    }


    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        print("Disconnected from peripheral: \(peripheral.name ?? "unknown")")

        DispatchQueue.main.async {
            self.connectedPeripheral = nil
            self.txCharacteristic = nil
            self.rxCharacteristic = nil
            self.wasDisconnected = true

            self.discoveredDevices.removeAll { $0.peripheral.identifier == peripheral.identifier }

            self.centralManager.stopScan()

            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                if self.centralManager.state == .poweredOn {
                    print("Starting scan again after disconnect (with delay)")
                    self.centralManager.scanForPeripherals(withServices: [CBUUID(string: "2222")], options: nil)
                }
            }

            self.sendDisconnectNotification(for: peripheral)
            NotificationCenter.default.post(name: .didDisconnectFromPeripheral, object: peripheral)
        }
    }



    func sendDisconnectNotification(for peripheral: CBPeripheral) {
        let content = UNMutableNotificationContent()
        content.title = "Bluetooth Disconnected"
        content.body = "\(peripheral.name ?? "Device") disconnected."
        content.sound = .default
        
        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Failed to send notification: \(error)")
            }
        }
    }
    
    
    
    
    
    // MARK: - CBPeripheralDelegate
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let services = peripheral.services {
            for service in services {
                peripheral.discoverCharacteristics(nil, for: service)
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let characteristics = service.characteristics {
            for characteristic in characteristics {
                switch characteristic.uuid {
                case CBUUID(string: "F9D1737F-65F8-5FE9-8025-0AD67E260AAF"):
                    txCharacteristic = characteristic
                    isDisconnectedByRemote = false
                    peripheral.setNotifyValue(true, for: characteristic)
                    print("TX characteristic found (Notify)")
                    
                case CBUUID(string: "F9D1737F-65F8-5FE9-8025-0AD67E260AAD"):
                    rxCharacteristic = characteristic
                    print("RX characteristic found (Write)")
                    
                case CBUUID(string: "F9D1737F-65F8-5FE9-8025-0AD67E260ABA"):
                    terminateCharacteristic = characteristic
                    peripheral.setNotifyValue(true, for: characteristic)
                    print("Terminate characteristic found (Notify)")
                    
                default:
                    break
                }
            }
        }
    }

    
    
    var incomingDataBuffer = Data()
    var messageBuffers: [UUID: String] = [:]

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard error == nil else {
            print("Error updating value: \(error!.localizedDescription)")
            return
        }

        guard let data = characteristic.value else {
            print("Invalid data received")
            return
        }

        let senderName = peripheral.name ?? "Unknown"

        if characteristic == terminateCharacteristic {
            if let message = String(data: data, encoding: .utf8), message == "DISCONNECT" {
                print("💥 Peripheral is shutting down")

                DispatchQueue.main.async {
                    if let topVC = UIApplication.shared.windows.first?.rootViewController {
                        let alert = UIAlertController(title: "Bluetooth Disconnected",
                                                      message: "\(peripheral.name ?? "Device") disconnected.",
                                                      preferredStyle: .alert)
                        alert.addAction(UIAlertAction(title: "OK", style: .default))
                        topVC.present(alert, animated: true)
                    }

                    self.isDisconnectedByRemote = true
                    self.connectedPeripheral = nil
                    self.txCharacteristic = nil
                    self.rxCharacteristic = nil

                    self.centralManager.stopScan()
                    if self.centralManager.state == .poweredOn {
                        print("🔄 Scanning for peripherals after DISCONNECT")
                        self.centralManager.scanForPeripherals(withServices: [CBUUID(string: "2222")], options: nil)
                    }

                    NotificationCenter.default.post(name: .didDisconnectFromPeripheral, object: peripheral)
                }

                return
            }
        }

        guard let chunk = String(data: data, encoding: .utf8) else {
            print("Unable to decode data chunk")
            return
        }

        incomingDataBuffer.append(data)

        guard let fullString = String(data: incomingDataBuffer, encoding: .utf8) else {
            print("Failed to decode buffer to string")
            return
        }

        let messages = fullString.components(separatedBy: "\n")

        for i in 0..<messages.count - 1 {
            let message = messages[i].trimmingCharacters(in: .whitespacesAndNewlines)
            if !message.isEmpty {
                DispatchQueue.main.async {
                    self.dataModel.addMessage(message, isSentByUser: false)
                    print("✅ Complete message received: \(message)")
                    self.sendLocalNotification(from: senderName, message: message)
                }
            }
        }

        if let last = messages.last, !fullString.hasSuffix("\n") {
            incomingDataBuffer = Data(last.utf8)
        } else {
            incomingDataBuffer.removeAll()
        }
    }

    
    
    
    
    private func sendLocalNotification(from sender: String, message: String) {
        let content = UNMutableNotificationContent()
        content.title = sender
        content.body = message
        content.sound = .default
        
        let request = UNNotificationRequest(identifier: UUID().uuidString,
                                            content: content,
                                            trigger: nil)
        
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Failed to schedule notification: \(error)")
            } else {
                print("Local notification scheduled.")
            }
        }
    }
    
    
    
    
    // MARK: - Public Send
    
    func sendText(_ text: String) {
        guard isConnected,
              let peripheral = connectedPeripheral,
              let characteristic = rxCharacteristic,
              let data = text.data(using: .utf8) else {
            print("Cannot send: Not connected or missing characteristic.")
            return
        }
        
        peripheral.writeValue(data, for: characteristic, type: .withoutResponse)
        print("Sent text to Mac: \(text)")
    }
}

extension Notification.Name {
    static let didReceiveBLEMessage = Notification.Name("didReceiveBLEMessage")
}



