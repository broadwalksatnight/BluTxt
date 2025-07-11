//
//  AppDelegate.m
//  BluTxt-Mac
//
//  Created by Jack McCaffrey on 19/5/25.
//

#import "AppDelegate.h"
#import "ViewController.h"
#import <CoreBluetooth/CoreBluetooth.h>
@import UserNotifications;


@interface AppDelegate () <CBPeripheralManagerDelegate, UNUserNotificationCenterDelegate>

@property (nonatomic, readwrite) CBPeripheralManager *peripheralManager;
@property (strong, nonatomic) CBMutableCharacteristic *rxCharacteristic;
@property (strong, nonatomic) CBMutableCharacteristic *txCharacteristic;
@property (strong, nonatomic) CBMutableCharacteristic *terminateCharacteristic;
@property (strong, nonatomic) CBMutableService *customService;
@property (nonatomic, strong) NSMutableArray<NSData *> *pendingChunks;
@property (nonatomic, assign) NSUInteger currentChunkIndex;


@property (nonatomic) BOOL isAdvertising;
@property (nonatomic, assign) BOOL isConnected;


@end

@implementation AppDelegate

- (ViewController *)mainViewController {
    NSWindow *window = [NSApplication sharedApplication].windows.firstObject;
    if (!window) return nil;
    NSViewController *vc = window.contentViewController;
    if ([vc isKindOfClass:[ViewController class]]) {
        return (ViewController *)vc;
    }
    return nil;
}

- (void)applicationDidFinishLaunching:(NSNotification *)aNotification {
    self.isAdvertising = NO;
    self.peripheralManager = [[CBPeripheralManager alloc] initWithDelegate:self queue:nil];
    
    [UNUserNotificationCenter currentNotificationCenter].delegate = self;
    
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    [center requestAuthorizationWithOptions:(UNAuthorizationOptionAlert | UNAuthorizationOptionSound)
                          completionHandler:^(BOOL granted, NSError * _Nullable error) {
        if (!granted) {
            NSLog(@"User denied notification permissions");
            return;
        }
        NSLog(@"Notification permission granted");

    }];

    NSString *savedUsername = [[NSUserDefaults standardUserDefaults] stringForKey:@"CustomUsername"];
    if (savedUsername.length > 0) {
        self.currentUsername = savedUsername;
        NSLog(@"Loaded saved username: %@", self.currentUsername);
    } else {
        self.currentUsername = [self generateRandomName];
        NSLog(@"Generated username: %@", self.currentUsername);
    }

    dispatch_async(dispatch_get_main_queue(), ^{
        ViewController *vc = [self mainViewController];
        if (vc) {
            [vc.nameButton setTitle:self.currentUsername];
        }
    });
}




- (NSApplicationTerminateReply)applicationShouldTerminate:(NSApplication *)sender {
    NSData *data = [@"DISCONNECT" dataUsingEncoding:NSUTF8StringEncoding];

    BOOL didSend = [self.peripheralManager updateValue:data
                                      forCharacteristic:self.terminateCharacteristic
                                   onSubscribedCentrals:nil];

    if (didSend) {
        NSLog(@"Sent DISCONNECT on terminateCharacteristic");
    } else {
        NSLog(@"Failed to send DISCONNECT on terminateCharacteristic");
    }

    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(200 * NSEC_PER_MSEC)), dispatch_get_main_queue(), ^{
        [sender replyToApplicationShouldTerminate:YES];
    });

    return NSTerminateLater;
}





- (BOOL)applicationShouldHandleReopen:(NSApplication *)sender hasVisibleWindows:(BOOL)flag {
    if (!flag) {
        NSWindow *mainWindow = [NSApplication sharedApplication].windows.firstObject;
        [mainWindow makeKeyAndOrderFront:nil];
    }
    return YES;
}

- (void)peripheralManagerDidUpdateState:(CBPeripheralManager *)peripheral {
    if (peripheral.state == CBManagerStatePoweredOn) {
        if (!self.isAdvertising) {
            NSLog(@"Bluetooth is ON - Setting up service");

            CBUUID *rxUUID = [CBUUID UUIDWithString:@"F9D1737F-65F8-5FE9-8025-0AD67E260AAD"];
            self.rxCharacteristic = [[CBMutableCharacteristic alloc] initWithType:rxUUID
                                                                       properties:CBCharacteristicPropertyWrite | CBCharacteristicPropertyWriteWithoutResponse
                                                                            value:nil
                                                                      permissions:CBAttributePermissionsWriteable];

            CBUUID *txUUID = [CBUUID UUIDWithString:@"F9D1737F-65F8-5FE9-8025-0AD67E260AAF"];
            self.txCharacteristic = [[CBMutableCharacteristic alloc] initWithType:txUUID
                                                                       properties:CBCharacteristicPropertyNotify
                                                                            value:nil
                                                                      permissions:CBAttributePermissionsReadable];
            
            CBUUID *terminateUUID = [CBUUID UUIDWithString:@"F9D1737F-65F8-5FE9-8025-0AD67E260ABA"];
            self.terminateCharacteristic = [[CBMutableCharacteristic alloc] initWithType:terminateUUID
                                                                              properties:CBCharacteristicPropertyNotify
                                                                                   value:nil
                                                                             permissions:CBAttributePermissionsReadable];


            self.customService = [[CBMutableService alloc] initWithType:[CBUUID UUIDWithString:@"2222"] primary:YES];
            self.customService.characteristics = @[self.rxCharacteristic, self.txCharacteristic, self.terminateCharacteristic];

            [self.peripheralManager addService:self.customService];

            NSString *customName = self.currentUsername;
            if (customName.length == 0) {

                customName = [self generateRandomName];
                self.currentUsername = customName;
            }

            [self restartAdvertisingWithCustomName:customName];
        }
    } else {
        NSLog(@"Bluetooth not available");
        self.isAdvertising = NO;
    }
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveWriteRequests:(NSArray<CBATTRequest *> *)requests {
    for (CBATTRequest *request in requests) {
        if ([request.characteristic.UUID isEqual:self.rxCharacteristic.UUID]) {
            NSData *data = request.value;
            NSString *receivedText = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
            NSLog(@"Received write: %@", receivedText);

            dispatch_async(dispatch_get_main_queue(), ^{
                ViewController *vc = [self mainViewController];
                if (vc) {
                    [vc.chatMessages addObject:[NSString stringWithFormat:@"%@", receivedText]];
                    [vc addChatMessage:[NSString stringWithFormat:@"%@", receivedText] fromUser:NO];
                }
            });

            [self.peripheralManager respondToRequest:request withResult:CBATTErrorSuccess];

            NSWindow *mainWindow = [NSApp mainWindow];
            if (mainWindow.isVisible) {
                NSLog(@"Main window is visible — skipping notification");
            } else {
                UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
                content.title = @"New BlutTXT Msg";
                content.body = receivedText;
                content.sound = [UNNotificationSound defaultSound];

                UNNotificationRequest *notificationRequest = [UNNotificationRequest requestWithIdentifier:[[NSUUID UUID] UUIDString]
                                                                                                  content:content
                                                                                                  trigger:nil];

                [[UNUserNotificationCenter currentNotificationCenter] addNotificationRequest:notificationRequest
                                                                       withCompletionHandler:^(NSError * _Nullable error) {
                    if (error) {
                        NSLog(@"Failed to post notification: %@", error);
                    } else {
                        NSLog(@"Notification posted for message");
                    }
                }];
            }
        }
    }
}



- (void)sendTextToCentralInChunks:(NSString *)text {
    NSData *fullData = [text dataUsingEncoding:NSUTF8StringEncoding];
    NSUInteger length = fullData.length;
    NSUInteger offset = 0;
    self.pendingChunks = [NSMutableArray array];

    while (offset < length) {
        NSUInteger chunkSize = MIN(524, length - offset);
        NSData *chunkData = [fullData subdataWithRange:NSMakeRange(offset, chunkSize)];
        [self.pendingChunks addObject:chunkData];
        offset += chunkSize;
    }

    self.currentChunkIndex = 0;

    [self sendNextChunk];
}

- (void)sendNextChunk {
    if (self.currentChunkIndex >= self.pendingChunks.count) {
        self.pendingChunks = nil;
        return;
    }

    NSData *chunk = self.pendingChunks[self.currentChunkIndex];
    BOOL didSend = [self.peripheralManager updateValue:chunk
                                      forCharacteristic:self.txCharacteristic
                                   onSubscribedCentrals:nil];

    if (didSend) {
        NSLog(@"Sent chunk %lu/%lu", (unsigned long)self.currentChunkIndex + 1, (unsigned long)self.pendingChunks.count);
        self.currentChunkIndex++;
        [self sendNextChunk];
    } else {
        NSLog(@"Waiting for peripheral to be ready to send next chunk...");
    }
}

- (void)peripheralManagerIsReadyToUpdateSubscribers:(CBPeripheralManager *)peripheral {
    [self sendNextChunk];
}


- (void)restartAdvertisingWithCustomName:(NSString *)name {
    if (self.isAdvertising) {
        [self.peripheralManager stopAdvertising];
        self.isAdvertising = NO;
        NSLog(@"Stopped advertising");
    }

    if (name.length == 0) {
        name = @"Mac";
    }
    
    

    NSString *advertisedName = [NSString stringWithFormat:@"(Mac) %@", name];
    if (advertisedName.length > 20) {
        advertisedName = [advertisedName substringToIndex:20];
    }

    NSLog(@"Restarting advertising with name: %@", advertisedName);

    [self.peripheralManager startAdvertising:@{
        CBAdvertisementDataServiceUUIDsKey: @[[CBUUID UUIDWithString:@"2222"]],
        CBAdvertisementDataLocalNameKey: advertisedName
    }];

    self.isAdvertising = YES;
    self.currentUsername = name;
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral
                   central:(CBCentral *)central
 didSubscribeToCharacteristic:(CBCharacteristic *)characteristic {
    NSLog(@"Central %@ subscribed to characteristic %@", central.identifier.UUIDString, characteristic.UUID.UUIDString);
    self.isConnected = YES;

    dispatch_async(dispatch_get_main_queue(), ^{
        ViewController *vc = [self mainViewController];
        if (vc) {
            [vc updateConnectionStatus:YES];
        }
    });
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral
                   central:(CBCentral *)central
didUnsubscribeFromCharacteristic:(CBCharacteristic *)characteristic {
    NSLog(@"Central %@ unsubscribed from characteristic %@", central.identifier.UUIDString, characteristic.UUID.UUIDString);
    self.isConnected = NO;

    dispatch_async(dispatch_get_main_queue(), ^{
        ViewController *vc = [self mainViewController];
        if (vc) {
            [vc updateConnectionStatus:NO];
        }
    });

}

- (NSString *)generateRandomName {
    NSArray *adjectives = @[@"Fast", @"Chill", @"Bright", @"Silent", @"Sharp", @"Lazy", @"Cool", @"Smart"];
    NSArray *nouns = @[@"Otter", @"Tiger", @"Fox", @"Bear", @"Wolf", @"Hawk", @"Duck", @"Dog"];

    NSString *adj = adjectives[arc4random_uniform((uint32_t)adjectives.count)];
    NSString *noun = nouns[arc4random_uniform((uint32_t)nouns.count)];
    int number = arc4random_uniform(100);

    return [NSString stringWithFormat:@"%@%@%02d", adj, noun, number];
}

static NSString * const kSavedUsernameKey = @"CustomUsername";

- (void)saveUsername:(NSString *)username {
    if (username.length > 0) {
        [[NSUserDefaults standardUserDefaults] setObject:username forKey:@"CustomUsername"];
        [[NSUserDefaults standardUserDefaults] synchronize];
        NSLog(@"Saved custom username: %@", username);
    }
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completionHandler {
    
    completionHandler(UNNotificationPresentationOptionAlert | UNNotificationPresentationOptionSound);
}



- (NSString *)loadSavedUsername {
    return [[NSUserDefaults standardUserDefaults] stringForKey:kSavedUsernameKey];
}



@end
