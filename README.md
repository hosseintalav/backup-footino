# Footino - Android Application

برنامه اندروید Footino - یک WebView سفارشی برای سایت https://hosseintalav.github.io/Footino/ به همراه سیستم اعلان مسابقات فوتبال و اعلان‌های عمومی.

## نسخه فعلی: v1.6

## ویژگی‌ها

- **WebView سفارشی** برای بارگذاری سایت Footino
- **Splash Screen** با انیمیشن ورود نرم و انتقال آنی به سایت (بدون انیمیشن انتقالی)
- **Status bar شفاف** مطابق تم اپ
- **Pull-to-Refresh** که فقط هنگام بالای صفحه بودن WebView فعال می‌شود (کلاس `NestedSwipeRefreshLayout`)
- **باز کردن لینک‌های خارجی** در مرورگر خارجی (با پشتیبانی کامل از `target="_blank"`)
- **اعلان مسابقات** - 90 دقیقه قبل از شروع مسابقات با `notify: true` در `matches.json`
- **اعلان‌های عمومی** - از فایل `notif.json` (با جلوگیری از ارسال تکراری)
- **بررسی پس‌زمینه دوره‌ای** - WorkManager هر 15 دقیقه JSON را بررسی می‌کند
- **بازیابی پس از reboot** - BootReceiver زمان‌بندی‌ها را پس از راه‌اندازی مجدد بازیابی می‌کند

## پیش‌نیازهای Build

| ابزار | نسخه | توضیح |
|------|------|-------|
| **JDK** | 17 یا بالاتر (توصیه: 21) | برای کامپایل Java و اجرای Gradle |
| **Android SDK** | Platform 34 + Build Tools 34.0.0 | برای build APK |
| **Gradle** | 8.7 (یا از طریق gradle wrapper) | سیستم build |

### نصب پیش‌نیازها (اختیاری - اگر Android Studio ندارید)

#### JDK 21 (از Adoptium Temurin):
```bash
curl -sSL -o /tmp/jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p ~/tools/jdk21
tar -xzf /tmp/jdk21.tar.gz -C ~/tools/jdk21 --strip-components=1
export JAVA_HOME=~/tools/jdk21
export PATH=$JAVA_HOME/bin:$PATH
```

#### Android SDK Command-line Tools:
```bash
wget -q "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -O /tmp/cmdline-tools.zip
mkdir -p ~/tools/android-sdk/cmdline-tools
unzip -q /tmp/cmdline-tools.zip -d ~/tools/android-sdk/cmdline-tools/
mv ~/tools/android-sdk/cmdline-tools/cmdline-tools ~/tools/android-sdk/cmdline-tools/latest
export ANDROID_HOME=~/tools/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
yes | sdkmanager --licenses
sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

#### Gradle 8.7:
```bash
wget -q "https://services.gradle.org/distributions/gradle-8.7-bin.zip" -O /tmp/gradle.zip
unzip -q /tmp/gradle.zip -d ~/tools/
```

## مراحل Build APK

### روش 1: استفاده از Gradle Wrapper (توصیه شده)

```bash
cd FootinoApp

# تنظیم متغیرهای محیطی
export ANDROID_HOME=~/tools/android-sdk
export JAVA_HOME=~/tools/jdk21
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

# Build نسخه Release
./gradlew assembleRelease

# APK ساخته شده در مسیر زیر:
# app/build/outputs/apk/release/app-release.apk
```

### روش 2: استفاده از Gradle نصب‌شده مستقیماً

```bash
cd FootinoApp
export ANDROID_HOME=~/tools/android-sdk
export JAVA_HOME=~/tools/jdk21
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

gradle clean assembleRelease
```

### روش 3: استفاده از Android Studio

1. پروژه را در Android Studio باز کنید: `File > Open > FootinoApp`
2. صبر کنید تا Gradle sync کامل شود
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` را انتخاب کنید
4. APK در `app/build/outputs/apk/release/` ساخته می‌شود

## ساختار پروژه

```
FootinoApp/
├── .gitignore
├── README.md                          # این فایل
├── build.gradle                       # top-level Gradle config
├── settings.gradle                    # تنظیمات ماژول‌ها و repositories
├── gradle.properties                  # تنظیمات Gradle
├── gradlew / gradlew.bat              # Gradle wrapper scripts
├── gradle/wrapper/                    # Gradle wrapper jar و properties
└── app/
    ├── build.gradle                   # تنظیمات ماژول app + dependencies
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml        # مجوزها و ثبت اکتیویتی‌ها و Receiver‌ها
        ├── java/com/footino/app/
        │   ├── MainActivity.java              # اکتیویتی اصلی (WebView + Splash)
        │   ├── NestedSwipeRefreshLayout.java  # Pull-to-Refresh سفارشی
        │   ├── NotificationHelper.java        # کانال‌ها و نمایش اعلان‌ها
        │   ├── MatchScheduler.java            # زمان‌بندی AlarmManager
        │   ├── AlarmReceiver.java             # BroadcastReceiver برای اعلان مسابقه
        │   ├── BootReceiver.java              # بازیابی alarm‌ها پس از reboot
        │   ├── MatchSyncWorker.java           # WorkManager Worker
        │   └── AnnouncementTracker.java       # ذخیره شناسه‌های اعلان عمومی
        └── res/
            ├── layout/activity_main.xml       # WebView + Splash overlay
            ├── values/colors.xml              # رنگ‌ها (#191919 و...)
            ├── values/themes.xml              # تم با status bar شفاف
            ├── values/strings.xml             # رشته‌ها
            ├── xml/network_security_config.xml
            ├── drawable/splash_icon.png       # آیکون splash (در چگالی‌های مختلف)
            ├── drawable-{m,h,xh,xxh,xxxh}dpi/ # splash_icon در چگالی‌های مختلف
            └── mipmap-{m,h,xh,xxh,xxxh}dpi/   # آیکون اپ در چگالی‌های مختلف
```

## سیستم اعلان مسابقات

### منبع داده
فایل `matches.json` از: `https://raw.githubusercontent.com/hosseintalav/Footino/refs/heads/main/matches.json`

### منطق اعلان
- فقط مسابقات با `"notify": true` اعلان می‌گیرند
- زمان اعلان: **90 دقیقه قبل از شروع مسابقه**
- منطقه زمانی: `Asia/Tehran` (UTC+3:30)
- عنوان اعلان: `team1 در مقابل team2` (مثلاً: `رئال مادرید در مقابل بارسلونا`)
- متن اعلان: `⚽ شروع مسابقه ساعت HH:MM` (با ارقام انگلیسی)

### مثال JSON:
```json
{
  "date": "2026-10-24",
  "time": "22:30",
  "team1": "رئال مادرید",
  "team2": "بارسلونا",
  "league": "لالیگا اسپانیا",
  "isClub": true,
  "countdown": true,
  "notify": true
}
```

### ذخیره‌سازی
- SharedPreferences: `footino_alarms`
- شامل: alarm_id، team1، team2، match_date، match_time، notify_at_millis
- شناسه پایدار: `hash(date + time + team1 + team2)`

### مکانیزم AlarmManager
- استفاده از `setExactAndAllowWhileIdle` (اندروید 6+)
- بررسی `canScheduleExactAlarms()` در اندروید 12+
- BootReceiver پس از reboot alarm‌ها را بازیابی می‌کند

## سیستم اعلان عمومی

### منبع داده
فایل `notif.json` از: `https://raw.githubusercontent.com/hosseintalav/Footino/refs/heads/main/notif.json`

### ساختار JSON:
```json
{
  "announcements": [
    {
      "id": "announcement-001",
      "title": "نسخه جدید Footino منتشر شد!",
      "message": "برای دریافت امکانات جدید، برنامه را به‌روزرسانی کنید."
    }
  ]
}
```

### منطق
- هر `id` فقط یک بار در هر نصب ارسال می‌شود (حتی پس از reboot یا دریافت مجدد JSON)
- تغییر `title` یا `message` برای همان `id` باعث ارسال مجدد نمی‌شود
- ذخیره در SharedPreferences جداگانه: `footino_announcements`
- دسترسی همگام‌سازی شده (`synchronized`) برای جلوگیری از ارسال دوگانه

### عملکرد لمس اعلان
- با لمس اعلان، اپ Footino باز می‌شود
- اگر اپ باز است، به پیش‌زمینه می‌آید (`FLAG_ACTIVITY_SINGLE_TOP`)

## بررسی پس‌زمینه (WorkManager)

- Worker: `MatchSyncWorker`
- دوره: هر **15 دقیقه** (حداقل ممکن در اندروید)
- محدودیت: نیاز به اتصال اینترنت (`NetworkType.CONNECTED`)
- وظایف:
  1. دریافت `matches.json` و زمان‌بندی/لغو اعلان‌های مسابقات
  2. دریافت `notif.json` و ارسال اعلان‌های عمومی جدید
  3. پاک‌سازی alarm‌های منقضی
- نام کار یکتا: `footino_match_sync_work` (جلوگیری از ثبت مجدد)

**نکته:** اندروید زمان دقیق اجرای Worker را تضمین نمی‌کند. در Doze mode ممکن است اجرا به تأخیر بیفتد.

## مجوزهای اندروید

| مجوز | نسخه | هدف |
|------|------|------|
| `INTERNET` | همه | دسترسی به اینترنت برای WebView و دریافت JSON |
| `ACCESS_NETWORK_STATE` | همه | بررسی وضعیت اینترنت |
| `POST_NOTIFICATIONS` | Android 13+ | نمایش اعلان (درخواست runtime) |
| `SCHEDULE_EXACT_ALARM` | Android 12+ | زمان‌بندی دقیق AlarmManager |
| `USE_EXACT_ALARM` | Android 13+ | جایگزین SCHEDULE_EXACT_ALARM |
| `RECEIVE_BOOT_COMPLETED` | همه | دریافت رویداد بوت برای بازیابی |
| `WAKE_LOCK` | همه | فعال کردن دستگاه هنگام firing alarm |

## اطلاعات نسخه

- **Package:** `com.footino.app`
- **VersionCode:** 3
- **VersionName:** 1.6
- **Min SDK:** 23 (Android 6.0)
- **Target SDK:** 34 (Android 14)
- **Compile SDK:** 34

## عیب‌یابی

### Build ناموفق به دلیل rate limit در Maven Central
این پروژه از mirrorهای Aliyun استفاده می‌کند (در `settings.gradle` و `build.gradle`). اگر همچنان مشکل داشتید:
- صبر کنید و دوباره تلاش کنید
- یا از VPN استفاده کنید

### کرش هنگام اجرا
- مطمئن شوید دستگاه Android 6.0+ دارد (minSdk 23)
- در Android 13+، مجوز اعلان را تأیید کنید

### اعلان دریافت نمی‌شود
1. مطمئن شوید مجوز اعلان داده شده: Settings > Apps > Footino > Notifications
2. مطمئن شوید `notify: true` در JSON تنظیم شده
3. مطمئن شوید زمان اعلان (90 دقیقه قبل از شروع) در آینده است
4. در اندروید 12+: Settings > Apps > Footino > Special permissions > Alarms & reminders را فعال کنید
5. battery optimization را برای Footino غیرفعال کنید

## توسعه‌دهنده

- **وب‌سایت:** https://hosseintalav.github.io/Footino/
- **مخزن بک‌آپ:** https://github.com/hosseintalav/backup-footino

## لایسنس

این پروژه خصوصی و متعلق به توسعه‌دهنده است.
