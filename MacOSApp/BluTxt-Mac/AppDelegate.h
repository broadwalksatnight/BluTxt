//
//  AppDelegate.h
//  BluTxt-Mac
//
//  Created by Jack McCaffrey on 19/5/25.
//

#import <Cocoa/Cocoa.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <UserNotifications/UserNotifications.h>

@interface AppDelegate : NSObject <NSApplicationDelegate, UNUserNotificationCenterDelegate>

@property (nonatomic, copy) NSString *currentUsername;

- (void)restartAdvertisingWithCustomName:(NSString *)name;
- (void)sendTextToCentralInChunks:(NSString *)text;


- (NSString *)generateRandomName;


@end


