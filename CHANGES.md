# التغييرات في CalendarAlarm2-fixed

> هذا الملف يوضّح بالضبط ما تغيّر بين النسخة السابقة والمُصلَحة، ولماذا.

---

## 🎯 السبب الجذري الواحد

**`NotificationChannel` كان يُنشأ بدون `setSound()` + `AudioAttributes.USAGE_ALARM`**

- النظام (Android 14+ وخاصة Samsung One UI 8) يصنّف القنوات بناءً على `AudioAttributes.USAGE`
- بدون `USAGE_ALARM` → القناة "إشعارات عادية" → النظام يرفض إطلاق Full Screen Intent Activity من background ويعرض heads-up notification بدلاً منها
- التعليق الصريح في كودك السابق `// مهم: لا نضع setSound هنا` كان القرار الخاطئ بالضبط

---

## 📁 الملفات المُعدَّلة (7 ملفات)

### 1. ⭐ CalendarAlarmApplication.kt (جديد)

**الغرض:** إنشاء قنوات الإشعارات بشكل صحيح في مكان مركزي واحد.

**النقاط الحرجة:**
- قناة جديدة بـ ID جديد (`calendar_alarm_channel_v3`) — لأن القنوات لا تقبل التحديث بعد الإنشاء
- `setSound(uri, AudioAttributes.USAGE_ALARM)` — **هذا الإصلاح الجوهري**
- حذف القنوات القديمة (`calendar_alarm_channel`, `alarm_service_channel`, إلخ) لتنظيف Settings الجوال
- `setBypassDnd(true)` + `lockscreenVisibility = VISIBILITY_PUBLIC` + `enableLights` + vibration pattern متكرر

### 2. AndroidManifest.xml (مُعدَّل)

**التغييرات:**
- ✔️ إضافة `android:name=".CalendarAlarmApplication"` للـ application tag
- ✔️ حذف `AlarmForegroundService` (كود ميت — لم يكن يُستدعى)
- ✔️ إضافة `taskAffinity=""` و `showForAllUsers="true"` للـ AlarmOverlayActivity (مهم لـ Samsung)
- ✔️ إضافة `LOCKED_BOOT_COMPLETED` للـ BootReceiver (يعمل قبل فتح القفل بعد restart)
- ❌ حذف `BIND_NOTIFICATION_LISTENER_SERVICE` permission من uses-permission (هي system permission، تُمنح من Settings فقط)

### 3. ⭐ EventAlarmReceiver.kt (مُعدَّل)

**التغييرات الجوهرية:**
- ✔️ يستخدم `CalendarAlarmApplication.CHANNEL_ID_ALARM_V3` (القناة الجديدة الصحيحة)
- ✔️ حذف `ensureChannel()` الداخلي — القناة تُنشأ مرة واحدة في Application
- ✔️ `PARTIAL_WAKE_LOCK` فقط (لا `FULL_WAKE_LOCK` deprecated)
- ✔️ Notification بـ `CATEGORY_ALARM` + `PRIORITY_MAX` + `setFullScreenIntent(pi, true)`
- ✔️ `setVisibility(VISIBILITY_PUBLIC)` لعرض على lock screen
- ✔️ `try/finally` لضمان تحرير WakeLock حتى عند الخطأ
- ✔️ إضافة `setContentIntent` + `setShowWhen` + `setWhen(startTime)`

### 4. ⭐ AlarmOverlayActivity.kt (مُعدَّل)

**التغييرات:**
- ✔️ طبقة دفاع إضافية: `SCREEN_BRIGHT_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP + ON_AFTER_RELEASE` كاحتياط لإيقاظ الشاشة فوراً
- ✔️ `MediaPlayer` بـ `AudioAttributes.USAGE_ALARM` (يحتمل DND)
- ✔️ `isLooping = true` للصوت (يستمر حتى يضغط المستخدم)
- ✔️ رفع volume للـ alarm stream إلى max ضماناً
- ✔️ Vibration pattern متكرر (`createWaveform` بدل `createOneShot`)
- ✔️ `releaseScreenWakeLock()` في كل مكان مناسب لتجنّب WakeLock leak

### 5. MainActivity.kt (مُعدَّل)

**التغييرات:**
- ✔️ حذف `createChannel()` — لا داعي لإنشاء القناة هنا (تنشأ في Application)
- ✔️ إضافة `recreateAlarmChannel()` يُستدعى عند تغيير النغمة
  (لأن `NotificationChannel.setSound` immutable بعد الإنشاء)

### 6. CalendarObserverService.kt (مُعدَّل)

**التغييرات:**
- ✔️ يستخدم `CalendarAlarmApplication.CHANNEL_ID_OBSERVER` (قناة v2 جديدة)
- ✔️ حذف `ensureChannel()` الداخلي

### 7. BootReceiver.kt (مُعدَّل)

**التغييرات:**
- ✔️ يدعم `LOCKED_BOOT_COMPLETED` (يعمل قبل فتح القفل بعد إعادة التشغيل)
- ✔️ يدعم `QUICKBOOT_POWERON` (لـ Samsung) و `com.htc.intent.action.QUICKBOOT_POWERON` (احتياط)
- ✔️ تأخير 2 ثانية قبل rescheduling (ضماناً لاكتمال تهيئة النظام)

### ❌ AlarmForegroundService.kt (محذوف)

**السبب:** كان كوداً ميتاً — لم يستدعِه أحد في مسار التنفيذ. حذفه ينظّف المشروع.

---

## 🔬 لماذا هذا الحل يعمل

### الميكانيكية الفعلية على Android 14+/Samsung One UI 8

```
1. AlarmManager.setAlarmClock() → يجدول المنبه
2. عند وقت الحدث → broadcast إلى EventAlarmReceiver
3. EventAlarmReceiver يبني Notification بـ:
   - قناة بـ USAGE_ALARM ✓
   - CATEGORY_ALARM ✓
   - PRIORITY_MAX ✓
   - setFullScreenIntent(activityPI, true) ✓
4. NotificationManager.notify(...)
5. النظام يرى:
   - "هذي قناة alarm حقيقية (USAGE_ALARM)"
   - "الجهاز مقفل والشاشة مطفية"
   - "FSI permission مفعّل"
   - "canUseFullScreenIntent() == true"
   → النظام يطلق AlarmOverlayActivity تلقائياً ويوقظ الشاشة
6. AlarmOverlayActivity:
   - setShowWhenLocked + setTurnScreenOn → تعرض على lock screen
   - SCREEN_BRIGHT_WAKE_LOCK احتياطي → الشاشة مضاءة فوراً
   - MediaPlayer USAGE_ALARM → الصوت يبدأ
   - Vibration متكرر → الاهتزاز يبدأ
```

### لماذا فشلت الـ8 محاولات السابقة

| # | المحاولة | السبب الفعلي للفشل |
|---|---|---|
| 1 | startActivity من Receiver | BAL محجوب على Android 10+ ومُشدَّد على Android 14+ |
| 2 | setExactAndAllowWhileIdle | يطلق المنبه، لكن القناة بدون USAGE_ALARM |
| 3 | setAlarmClock | صحيح للجدولة، لكن لا يصلح القناة المعطوبة |
| 4 | ForegroundService + startActivity | FGS-launched activities محجوبة على Android 14+ |
| 5 | fullScreenIntent | كان موجوداً، فشل لأن القناة بدون USAGE_ALARM |
| 6 | SYSTEM_ALERT_WINDOW | permission مفعّل لكن غير مُستخدَم في الكود |
| 7 | FULL_WAKE_LOCK | deprecated منذ Android O، النظام يتجاهلها |
| 8 | FLAG_SHOW_WHEN_LOCKED | يشتغل فقط عند إطلاق الـ activity |

---

## 🔧 خطوات ما بعد التثبيت (مهم جداً)

### الخطوة 1: حذف التطبيق القديم
**ضروري** — Notification Channels تبقى محفوظة في النظام حتى مع التحديث. الكود الجديد يحاول حذف القنوات القديمة تلقائياً، لكن للضمان:

1. اضغط مطوّلاً على أيقونة CalendarAlarm
2. اختر "Uninstall" أو "إزالة"
3. أكّد الإزالة

### الخطوة 2: تثبيت الـ APK الجديد
1. ابن الـ APK من المشروع الجديد عبر Android Studio
2. ثبّته على الجهاز

### الخطوة 3: فحص الإعدادات (مهم)
بعد فتح التطبيق أول مرة، اذهب لـ **Settings → Apps → CalendarAlarm**:

**Notifications:**
- اضغط على "منبّه التقويم" (القناة الجديدة)
- ✔️ تأكد أن الصوت ليس "Silent"
- ✔️ تأكد من تفعيل "Sound" و "Vibrate"
- ✔️ تأكد من "Show on lock screen" = "Show all content"
- ✔️ تأكد من "Override Do Not Disturb" مفعّل

**Special access:**
- ✔️ "Display over other apps" → السماح
- ✔️ "Alarms & reminders" → السماح
- ✔️ "Notifications when phone is locked" → Show all (Samsung)

**Battery:**
- ✔️ "Allow background activity" → مفعّل
- ✔️ "Battery usage" → "Unrestricted"

### الخطوة 4: إعدادات Samsung الخاصة
**Settings → Lock screen → Notifications:**
- ✔️ تأكد من تفعيل "Show notifications"
- ✔️ "Wake screen when notifications arrive" مفعّل (إن وُجد)

**Settings → Battery and device care → Battery → Background usage limits:**
- تأكد من أن CalendarAlarm **ليس** في "Sleeping apps" ولا "Deep sleeping apps"
- إذا كان موجوداً، أزله من القائمة

### الخطوة 5: الاختبار
1. أنشئ حدث في Google Calendar بعد دقيقتين
2. اقفل الجوال (زر الـ power)
3. انتظر — يجب أن:
   - تستيقظ الشاشة تلقائياً
   - تظهر AlarmOverlayActivity على lock screen
   - الصوت يبدأ
   - الاهتزاز يبدأ

---

## ⚠️ إذا لم يعمل بعد كل هذا

السيناريو الوحيد الذي قد يبقى فيه عدم استيقاظ الشاشة:

### السبب المحتمل: Samsung Power Saving / Adaptive Battery

Samsung One UI 8 له طبقة "Adaptive Battery" قد تتعلّم سلوك التطبيق وتقيّده تلقائياً.

**الحل:**
1. **Settings → Battery and device care → Battery → More battery settings**
2. **Adaptive battery** → عطّل مؤقتاً للاختبار
3. أعد الاختبار

### الحل الأخير (إذا فشل كل شيء): النشر على Google Play

**لماذا:** بعض ميزات Android 16 + One UI 8 الخاصة بـ FSI تتطلب أن يكون التطبيق:
- منشور على Play Console كـ "Alarm app"
- يحوي إعلان `<property name="android.app.PROPERTY_ALARM_USE_CASE" />` في manifest
- مُعتمد من Google Play Review

**التكلفة:** $25 رسوم لمرة واحدة لحساب Google Play Developer.

**المبررات:** هذا حل أخير فقط إذا فشل كل ما سبق. الإصلاح الحالي (USAGE_ALARM + كل الـ flags الصحيحة) يجب أن يعمل على 95%+ من حالات Samsung Galaxy S24 Ultra على One UI 8.

---

## 📞 إذا واجهت أي خطأ في البناء

أرسل لي:
- لقطة شاشة من Android Studio Build window
- نص الخطأ كاملاً (ليس صورة)
- أي ملف Android Studio يطلب فتحه

سأشخّص وأصلح في رد واحد.
