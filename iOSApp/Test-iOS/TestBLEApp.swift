//
//  TestBLEApp.swift
//  Test-iOS
//
//  Created by Jack McCaffrey on 22/5/25.
//


import SwiftUI
import UserNotifications

@main
struct blutxtApp: App {
    @StateObject private var dataModel = BluetoothDataModel()
    @StateObject private var bleManager: BLECentralManager
    private let notificationHandler = NotificationHandler()

    init() {
        let model = BluetoothDataModel()
        _dataModel = StateObject(wrappedValue: model)
        _bleManager = StateObject(wrappedValue: BLECentralManager(dataModel: model))

        UNUserNotificationCenter.current().delegate = notificationHandler
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(dataModel)
                .environmentObject(bleManager)
                .onAppear {
                    // Assign bleManager to notificationHandler only after it's initialized by SwiftUI
                    notificationHandler.bleManager = bleManager
                    requestNotificationPermission()
                }
        }
    }

    func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { granted, error in
            if let error = error {
                print("Notification permission error: \(error.localizedDescription)")
            } else {
                print("Notification permission granted: \(granted)")
            }
        }
    }
}


// MARK: - NotificationHandler

class NotificationHandler: NSObject, UNUserNotificationCenterDelegate {
    var bleManager: BLECentralManager?
}
