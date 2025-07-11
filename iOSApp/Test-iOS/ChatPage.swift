//
//  ChatPage.swift
//  Test-iOS
//
//  Created by Jack McCaffrey on 24/6/25.
//


import SwiftUI

struct ChatPage: View {
    @EnvironmentObject var dataModel: BluetoothDataModel
    @EnvironmentObject var bleManager: BLECentralManager
    @State private var textToSend = ""
    @State private var copiedMessageID: UUID?
    
    var body: some View {
        VStack {
            ScrollViewReader { scrollProxy in
                ScrollView {
                    MessagesView(messages: dataModel.messages, copiedMessageID: $copiedMessageID)
                        .padding()
                }
                .onChange(of: dataModel.messages.count) { _ in
                    if let lastID = dataModel.messages.last?.id {
                        scrollProxy.scrollTo(lastID, anchor: .bottom)
                    }
                }
            }
            
            Divider()
            
            HStack {
                TextField("Enter message", text: $textToSend)
                    .disabled(!(bleManager.isConnected && bleManager.txCharacteristic != nil))
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .submitLabel(.send)
                    .onSubmit {
                        sendMessage()
                    }

                Button("Send") {
                    sendMessage()
                }
                .disabled(!(bleManager.isConnected && bleManager.txCharacteristic != nil && !textToSend.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty))


            }
            .padding()
        }
        .navigationTitle("Chat")
    }
    
    private func sendMessage() {
        guard bleManager.isConnected else { return }
        let trimmed = textToSend.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        
        bleManager.sendText(trimmed)
        dataModel.addMessage(trimmed, isSentByUser: true)
        textToSend = ""
    }
}
