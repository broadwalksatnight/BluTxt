//
//  ViewController.m
//  BluTxt-Mac
//
//  Created by Jack McCaffrey on 19/5/25.
//

#import "ViewController.h"
#import "AppDelegate.h"
@import UserNotifications;

@implementation ViewController


- (void)viewDidLoad {
    [super viewDidLoad];
    
    self.connectionStatusView.wantsLayer = YES;
    self.connectionStatusView.layer.backgroundColor = [NSColor systemRedColor].CGColor;
    self.connectionStatusView.layer.cornerRadius = self.connectionStatusView.frame.size.height / 2;
    self.connectionStatusView.layer.masksToBounds = YES;
    self.sendTextField.enabled = NO;
    self.sendTextField.placeholderString = @"No connection established";

    self.sendTextField.delegate = self;
    self.chatMessages = [NSMutableArray array];
    self.chatStackView.edgeInsets = NSEdgeInsetsMake(4, 4, 4, 4);

    self.activeSounds = [NSMutableArray array];
    
    NSLayoutConstraint *minHeightConstraint = [self.chatStackView.heightAnchor constraintGreaterThanOrEqualToConstant:30];
    minHeightConstraint.priority = NSLayoutPriorityDefaultLow;
    minHeightConstraint.active = YES;

    AppDelegate *delegate = (AppDelegate *)[NSApplication sharedApplication].delegate;
    NSString *username = delegate.currentUsername ?: @"";
    [self.nameButton setTitle:username];
}


- (IBAction)labelButtonClicked:(id)sender {
    NSAlert *alert = [[NSAlert alloc] init];
    alert.messageText = @"Enter your username:";
    alert.informativeText = @"Max length 14 Characters";
    [alert addButtonWithTitle:@"OK"];
    [alert addButtonWithTitle:@"Random"];

    NSTextField *input = [[NSTextField alloc] initWithFrame:NSMakeRect(0, 0, 200, 24)];
    input.stringValue = self.nameButton.title ?: @"";
    alert.accessoryView = input;

    NSModalResponse response = [alert runModal];

    NSString *finalName = nil;

    if (response == NSAlertFirstButtonReturn) {
        finalName = input.stringValue;
    } else if (response == NSAlertSecondButtonReturn) {
        AppDelegate *delegate = (AppDelegate *)[NSApplication sharedApplication].delegate;
        finalName = [delegate generateRandomName];
    }

    if (finalName.length > 14) {
        finalName = [finalName substringToIndex:14];
    }

    self.nameButton.title = finalName;
    AppDelegate *delegate = (AppDelegate *)[NSApplication sharedApplication].delegate;
    delegate.currentUsername = finalName;
    [delegate restartAdvertisingWithCustomName:finalName];

    [[NSUserDefaults standardUserDefaults] setObject:finalName forKey:@"CustomUsername"];
    [[NSUserDefaults standardUserDefaults] synchronize];

    NSLog(@"✅ New username set: %@", finalName);
}



- (IBAction)sendMessage:(id)sender {
    NSString *text = self.sendTextField.stringValue;

    NSUInteger maxLength = 524;

    if (text.length == 0) {
        return;
    }

    if (text.length > maxLength) {
      ///  NSAlert *alert = [[NSAlert alloc] init];
      ///  alert.messageText = @"Message too long";
       /// alert.informativeText = [NSString stringWithFormat:@"Your message exceeds the maximum allowed length of %lu characters.", (unsigned long)maxLength];
       /// [alert addButtonWithTitle:@"OK"];
       /// [alert runModal];
        
    }

    NSString *messageWithDelimiter = [text stringByAppendingString:@"\n"];

      AppDelegate *appDelegate = (AppDelegate *)[NSApp delegate];
      [appDelegate sendTextToCentralInChunks:messageWithDelimiter];

    [self addChatMessage:text fromUser:YES];
    self.sendTextField.stringValue = @"";
    [self playSoundNamed:@"Pop"];
}



- (void)updateConnectionStatus:(BOOL)isConnected {
    dispatch_async(dispatch_get_main_queue(), ^{
        self.sendTextField.enabled = isConnected;

        if (isConnected) {
            self.connectionStatusView.wantsLayer = YES;
            self.connectionStatusView.layer.backgroundColor = [NSColor systemBlueColor].CGColor;

            self.sendTextField.placeholderString = @"Type something..";

            [[NSSound soundNamed:@"Submarine"] play];
        } else {
            self.connectionStatusView.wantsLayer = YES;
            self.connectionStatusView.layer.backgroundColor = [NSColor systemRedColor].CGColor;

            self.sendTextField.placeholderString = @"No connection established";

            [[NSSound soundNamed:@"Submarine"] play];
        }
    });
}



- (void)addChatMessage:(NSString *)message fromUser:(BOOL)isSelf {
    [NSAnimationContext runAnimationGroup:^(NSAnimationContext *context) {
        context.duration = 0;
        
        
        NSStackView *messageContainer = [self createMessageContainer];
        messageContainer.distribution = NSStackViewDistributionFill;

        NSTextField *messageLabel = [self createMessageLabelWithText:message isSelf:isSelf];
        NSTextField *timestampLabel = [self createTimestampLabel];

        NSView *spacer = [self createHorizontalSpacer];
        [spacer setContentHuggingPriority:NSLayoutPriorityDefaultLow forOrientation:NSLayoutConstraintOrientationHorizontal];
        [spacer setContentCompressionResistancePriority:NSLayoutPriorityDefaultLow forOrientation:NSLayoutConstraintOrientationHorizontal];

        if (isSelf) {
            [messageContainer addArrangedSubview:messageLabel];
            [messageContainer addArrangedSubview:timestampLabel];
            [messageContainer addArrangedSubview:spacer];
        } else {
            [messageContainer addArrangedSubview:spacer];
            [messageContainer addArrangedSubview:messageLabel];
            [messageContainer addArrangedSubview:timestampLabel];

            [self playSoundNamed:@"Pop"];
        }

        [self.chatStackView addArrangedSubview:messageContainer];
        
        [self.chatStackView layoutSubtreeIfNeeded];
        [self.chatScrollView.contentView layoutSubtreeIfNeeded];

    } completionHandler:^{
        dispatch_async(dispatch_get_main_queue(), ^{
            [self scrollToBottomIfNeeded];
        });
    }];
    if (!isSelf && ![NSApp isActive]) {
         [self showNotificationWithMessage:message];
     }
}





- (NSStackView *)createMessageContainer {
    NSStackView *stack = [[NSStackView alloc] init];
    stack.orientation = NSUserInterfaceLayoutOrientationHorizontal;
    stack.spacing = 4;
    stack.alignment = NSLayoutAttributeTop;
    return stack;
}

- (NSTextField *)createMessageLabelWithText:(NSString *)text isSelf:(BOOL)isSelf {
    NSTextField *label = [[NSTextField alloc] init];
    label.editable = NO;
    label.bezeled = NO;
    label.drawsBackground = NO;
    label.lineBreakMode = NSLineBreakByWordWrapping;
    label.usesSingleLineMode = NO;
    label.selectable = YES;
    label.preferredMaxLayoutWidth = 400;
    label.stringValue = text;
    label.font = [NSFont systemFontOfSize:13];
    label.textColor = isSelf ? [NSColor systemBlueColor] : [NSColor systemTealColor];
    return label;
}

- (NSTextField *)createTimestampLabel {
    NSTextField *label = [[NSTextField alloc] init];
    label.editable = NO;
    label.bezeled = NO;
    label.drawsBackground = NO;
    label.font = [NSFont systemFontOfSize:9];
    label.textColor = [NSColor secondaryLabelColor];
    label.stringValue = [self formattedTimestamp];
    return label;
}

- (NSView *)createHorizontalSpacer {
    NSView *spacer = [[NSView alloc] init];
    spacer.translatesAutoresizingMaskIntoConstraints = NO;
    [spacer setContentHuggingPriority:NSLayoutPriorityDefaultLow forOrientation:NSLayoutConstraintOrientationHorizontal];
    [spacer setContentCompressionResistancePriority:NSLayoutPriorityDefaultLow forOrientation:NSLayoutConstraintOrientationHorizontal];
    return spacer;
}

- (void)scrollToBottomIfNeeded {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSScrollView *scrollView = self.chatScrollView;
        NSClipView *clipView = scrollView.contentView;
        NSView *documentView = scrollView.documentView;

        CGFloat contentHeight = documentView.bounds.size.height;
        CGFloat visibleHeight = clipView.bounds.size.height;

        if (contentHeight > visibleHeight) {
            NSRect visibleRect = clipView.documentVisibleRect;
            CGFloat bottomOffset = NSMaxY(documentView.bounds) - NSMaxY(visibleRect);

            BOOL isAtBottom = bottomOffset <= 10.0;

            if (isAtBottom) {
                NSPoint newOrigin = NSMakePoint(0, contentHeight - visibleHeight);
                [clipView scrollToPoint:newOrigin];
                [scrollView reflectScrolledClipView:clipView];
            }
        }
    });
}




- (NSString *)formattedTimestamp {
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.dateFormat = @"HH:mm";
    return [formatter stringFromDate:[NSDate date]];
}

- (void)playSoundNamed:(NSString *)soundName {
    NSString *soundPath = [@"/System/Library/Sounds" stringByAppendingPathComponent:[NSString stringWithFormat:@"%@.aiff", soundName]];
    NSSound *sound = [[NSSound alloc] initWithContentsOfFile:soundPath byReference:YES];
    if (!sound) {
        NSLog(@"Failed to load sound at path: %@", soundPath);
        return;
    }
    sound.delegate = self;
    [self.activeSounds addObject:sound];
    [sound play];
}


- (void)sound:(NSSound *)sound didFinishPlaying:(BOOL)finished {
    [self.activeSounds removeObject:sound];
}

- (void)sendMessage:(NSString *)fullMessage fromUser:(BOOL)isSelf {
    NSUInteger maxLength = 524;
    NSUInteger length = fullMessage.length;
    NSUInteger start = 0;
    
    while (start < length) {
        NSUInteger chunkLength = MIN(maxLength, length - start);
        NSString *chunk = [fullMessage substringWithRange:NSMakeRange(start, chunkLength)];
        
        [self addChatMessage:chunk fromUser:isSelf];
        
        start += chunkLength;
    }
}

- (void)showNotificationWithMessage:(NSString *)message {
    UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
    content.title = @"New Message";
    content.body = message;
    content.sound = [UNNotificationSound defaultSound];

    UNTimeIntervalNotificationTrigger *trigger = [UNTimeIntervalNotificationTrigger triggerWithTimeInterval:1 repeats:NO];
    UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:[[NSUUID UUID] UUIDString] content:content trigger:trigger];

    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    [center addNotificationRequest:request withCompletionHandler:^(NSError * _Nullable error) {
        if (error) {
            NSLog(@"Error showing notification: %@", error);
        }
    }];
}





@end
