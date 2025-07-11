//
//  ViewController.h
//  BluTxt-Mac
//
//  Created by Jack McCaffrey on 19/5/25.
//

#import <Cocoa/Cocoa.h>

@interface ViewController : NSViewController <NSTextFieldDelegate>


@property (strong) NSMutableArray<NSString *> *chatMessages;
@property (strong) IBOutlet NSTextField *sendTextField;
@property (weak) IBOutlet NSButton *nameButton;
@property (weak) IBOutlet NSView *connectionStatusView;
@property (weak) IBOutlet NSStackView *chatStackView;
@property (weak) IBOutlet NSScrollView *chatScrollView;
@property (nonatomic, strong) NSMutableArray<NSSound *> *activeSounds;


- (void)updateConnectionStatus:(BOOL)isConnected;
- (void)addChatMessage:(NSString *)message fromUser:(BOOL)isUser;


@end



