//
//  ContentView.swift
//  Test-iOS
//
//  Created by Jack McCaffrey on 12/6/25.
//


import SwiftUI
import CoreBluetooth

struct ContentView: View {
    @EnvironmentObject var bleManager: BLECentralManager
    @State private var selectedTab = 1
    
    var body: some View {
        TabView(selection: $selectedTab) {
            ChatPage()
                .tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right") }
                .tag(0)

            DevicesPage(selectedTab: $selectedTab)
                .tabItem { Label("Devices", systemImage: "antenna.radiowaves.left.and.right") }
                .tag(1)
        }
        .accentColor(
            (bleManager.connectedPeripheral != nil && !bleManager.isDisconnectedByRemote) ? .green : .red
        )
    }
}



struct DevicesPage: View {
    @EnvironmentObject var bleManager: BLECentralManager
    @Binding var selectedTab: Int
    
    var body: some View {
        VStack(alignment: .leading) {
            Text("Available Devices")
                .font(.title2)
                .padding()

            List(bleManager.discoveredDevices) { device in
                Button(action: {
                    bleManager.connect(to: device)
                }) {
                    HStack {
                        Text(device.name)
                        Spacer()
                        if bleManager.connectedPeripheral == device.peripheral {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                        } else if bleManager.connectingPeripheral == device.peripheral {
                            ProgressView()
                        }
                    }
                }
            }
        }
        .padding(.top)
        .onAppear {
            bleManager.discoveredDevices.removeAll()
            if bleManager.centralManagerState == .poweredOn {
                bleManager.startScan()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .didConnectToPeripheral)) { _ in
            selectedTab = 0  
        }
        .onReceive(NotificationCenter.default.publisher(for: .didDisconnectFromPeripheral)) { _ in
            bleManager.discoveredDevices.removeAll()
            if bleManager.centralManagerState == .poweredOn {
                bleManager.startScan()
            }
        }
    }
}
