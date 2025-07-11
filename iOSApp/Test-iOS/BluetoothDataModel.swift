//
//  BluetoothDataModel.swift
//  Test-iOS
//
//  Created by Jack McCaffrey on 12/6/25.
//


import Foundation

struct ChatMessage: Identifiable {
    let id = UUID()
    let text: String
    let isSentByUser: Bool
    let timestamp: Date
}

class BluetoothDataModel: ObservableObject {
    @Published var messages: [ChatMessage] = []

    func addMessage(_ text: String, isSentByUser: Bool) {
        let message = ChatMessage(text: text, isSentByUser: isSentByUser, timestamp: Date())
        DispatchQueue.main.async {
            self.messages.append(message)
        }
    }
}
