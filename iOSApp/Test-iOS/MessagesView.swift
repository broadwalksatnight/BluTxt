//
//  MessagesView.swift
//  Test-iOS
//
//  Created by Jack McCaffrey on 24/6/25.
//


import SwiftUI

struct MessagesView: View {
    let messages: [ChatMessage]
    @Binding var copiedMessageID: UUID?

    let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(messages) { message in
                VStack(alignment: message.isSentByUser ? .leading : .trailing, spacing: 2) {
                    HStack {
                        if !message.isSentByUser { Spacer() }

                        Text(message.text)
                            .padding(10)
                            .background(
                                (copiedMessageID == message.id
                                 ? Color.white.opacity(0.1)
                                 : (message.isSentByUser ? Color.blue.opacity(0.6) : Color.teal.opacity(0.6)))
                                .animation(.easeInOut(duration: 0.3), value: copiedMessageID)
                            )
                            .cornerRadius(10)
                            .foregroundColor(
                                copiedMessageID == message.id ? .white : .primary
                            )
                            .animation(.easeInOut(duration: 0.3), value: copiedMessageID)
                            .onTapGesture {
                                UIPasteboard.general.string = message.text

                                withAnimation(.easeInOut(duration: 0.3)) {
                                    copiedMessageID = message.id
                                }

                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                    withAnimation(.easeInOut(duration: 0.3)) {
                                        copiedMessageID = nil
                                    }
                                }
                            }


                        if message.isSentByUser { Spacer() }
                    }

                    if !message.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Text(dateFormatter.string(from: message.timestamp))
                            .font(.caption2)
                            .foregroundColor(.gray)
                            .padding(message.isSentByUser ? .leading : .trailing, 16)
                    }
                }
                .id(message.id)
            }
        }
    }
}
