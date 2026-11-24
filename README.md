# مدیریت هزینه‌ها (مخارج) — اپلیکیشن اندروید

اپلیکیشن اندرویدی مدیریت هزینه‌ها و امور مالی شخصی با **Kotlin**، **Jetpack Compose (Material 3)**، **Room Database** و پشتیبانی کامل از تقویم شمسی/میلادی.

## پیش‌نیازهای بیلد محلی
- **Android Studio** (نسخه سازگار با AGP 9.1.1)
- **JDK 21** (Temurin / OpenJDK 21)
- **Gradle Wrapper 9.3.1** (به صورت آماده در مخزن قرار دارد)

### دستورات ساخت محلی
```bash
chmod +x ./gradlew
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## ساخت خودکار APK در GitHub Actions

با هر بار `push` روی شاخه `main` یا `master` و یا اجرای دستی از تب **Actions** (`workflow_dispatch`)، ورک‌فلو `.github/workflows/android.yml` به صورت خودکار:
1. محیط **JDK 21** و کش **Gradle** را تنظیم می‌کند.
2. تست‌های واحد (`testDebugUnitTest`) را اجرا می‌کند.
3. فایل **Debug APK** قابل نصب مستقیم روی گوشی (`Hazineh-Debug-APK`) را می‌سازد.
4. فایل **Release APK** امضاشده (`Hazineh-Release-APK`) را می‌سازد (در صورت نبود کلید شخصی در Secrets، به صورت خودکار با کلید امضای موقت CI امضا می‌شود تا بیلد متوقف نشود).
5. هر دو فایل APK را در بخش **Artifacts** همان اجرا برای دانلود آپلود می‌کند.

## تنظیمات اختیاری GitHub Secrets (برای امضای رسمی Release)

برای ساخت بدون تنظیم دستی، هیچ Secret اجباری نیست. اما اگر می‌خواهید نسخه `Release APK` با کلید دائمی و شخصی خودتان امضا شود، در مسیر `Settings → Secrets and variables → Actions` مخزن، مقادیر زیر را تعریف کنید:

| نام Secret | توضیح |
|---|---|
| `KEYSTORE_BASE64` | محتوای Base64 فایل کلید `my-upload-key.jks` (با `alias = upload`) |
| `STORE_PASSWORD` | رمز فروشگاه کلید (Keystore Password) |
| `KEY_PASSWORD` | رمز کلید `upload` (Key Password) |
| `GOOGLE_SERVICES_JSON_BASE64` | (اختیاری) محتوای Base64 فایل `app/google-services.json` در صورت حذف آن از مخزن |

ساخت کلید شخصی و تبدیل به Base64 (در صورت نیاز):
```bash
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 my-upload-key.jks
```
