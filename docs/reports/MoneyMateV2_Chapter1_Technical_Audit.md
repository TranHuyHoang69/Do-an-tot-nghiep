# MoneyMate v2 - Báo cáo phân tích kỹ thuật phục vụ Chương 1

Báo cáo này được lập bằng cách đọc trực tiếp repository hiện tại tại `C:\Users\Admin\AndroidStudioProjects\MoneyMatev2`. Nội dung ưu tiên bằng chứng từ source code, không khẳng định công nghệ hoặc thuật toán nếu chỉ thấy dependency nhưng chưa thấy nơi sử dụng.

Lưu ý bảo mật: repository có file cấu hình Firebase/Google (`app/google-services.json`) và resource client id phục vụ Google Sign-In. Báo cáo chỉ ghi nhận sự tồn tại/cách tham chiếu, không trích xuất hoặc sao chép giá trị định danh, token, API key hay nội dung nhạy cảm.

## 1. Tổng quan kiến trúc MoneyMate v2

MoneyMate v2 là ứng dụng Android một module (`:app`) viết bằng Kotlin, giao diện Jetpack Compose, có phân lớp theo hướng `presentation`, `domain`, `data`, `core`. Kiến trúc thực tế có dấu hiệu Clean Architecture và MVVM, nhưng chưa hoàn toàn tách biệt tuyệt đối vì một số model/enum của tầng data vẫn được domain/presentation tham chiếu trực tiếp, ví dụ `TransactionType`, `RepeatRule`.

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| Phân tầng Presentation/Domain/Data/Core | `presentation` chứa Screen/ViewModel; `domain` chứa model, repository interface, use case; `data` chứa Room entity/DAO, repository implementation, DTO, worker/reminder; `core` chứa DI, util, reminder session. | `app/src/main/java/com/example/moneymatev2/presentation`, `domain`, `data`, `core` | Đã triển khai và sử dụng | Bắt buộc |
| MVVM | ViewModel quản lý `StateFlow`, `MutableStateFlow`, Compose `mutableStateOf`; Screen quan sát state qua `collectAsState`. | `HomeViewModel`, `BudgetViewModel`, `ReminderViewModel`, `AuthViewModel`; `HomeScreen`, `BudgetScreen`, `ReminderScreen` | Đã triển khai và sử dụng | Bắt buộc |
| Clean Architecture thực dụng | UseCase điều phối logic nghiệp vụ và repository interface tách khỏi implementation; tuy nhiên domain còn phụ thuộc một số enum/entity của data. | `CreateTransactionUseCase`, `GetMonthlyBudgetOverviewUseCase`, `SyncManager`; `domain/repository/*`; tham chiếu `data.local.entity.TransactionType` trong domain | Đã áp dụng một phần | Bắt buộc, trình bày kèm giới hạn |
| Repository Pattern | Interface ở `domain/repository`, implementation ở `data/repository`, binding qua Hilt. | `TransactionRepository.kt` và `TransactionRepositoryImpl.kt`; `RepositoryModule.bindTransactionRepository` | Đã triển khai và sử dụng | Bắt buộc |
| Dependency Injection bằng Hilt | `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Module`, `@Binds`, `@Provides`, `@HiltWorker`. | `App.kt`, `MainActivity.kt`, `DatabaseModule`, `RepositoryModule`, `SyncWorker` | Đã triển khai và sử dụng | Bắt buộc |
| Quản lý trạng thái UI | State được biểu diễn bằng sealed class/data class và Flow/StateFlow; sự kiện một lần dùng `Channel.receiveAsFlow`. | `HomeUiState`, `BudgetUiState`, `ReminderUiState`, `AuthUiState`, `AddTransactionEvent`, `ReminderEvent` | Đã triển khai và sử dụng | Bắt buộc |
| Điều hướng | Navigation Compose với route sealed class `Screen`, `NavHost`, `composable`, argument và `savedStateHandle` để chọn category. | `navigation/Screen.kt`, `navigation/NavGraph.kt` | Đã triển khai và sử dụng | Bắt buộc |
| Luồng dữ liệu chính | UI -> ViewModel -> UseCase -> Repository interface -> Repository impl -> DAO/Firestore; dữ liệu local Room phát Flow ngược về ViewModel/UI. | `AddTransactionViewmodel.save`, `CreateTransactionUseCase.invoke`, `TransactionRepositoryImpl.createTransaction`, `TransactionDao.getAllTransactions`; `GetTransactionWithCategoryUseCase` | Đã triển khai và sử dụng | Bắt buộc |
| Khởi tạo ứng dụng | `Application` cấu hình Hilt WorkManager, áp theme đã lưu, lập lịch sync định kỳ, khởi chạy quản lý reminder theo auth session. | `App.onCreate`, `App.workManagerConfiguration` | Đã triển khai và sử dụng | Nên trình bày |
| Drawer/top-level app shell | Drawer lấy tổng số dư từ `HomeViewModel`, route hiện tại từ `NavController`. | `AppRoot.AppContent`, `AppDrawer`, `DrawerViewModel` | Đã triển khai và sử dụng | Chỉ đề cập ngắn |

### Luồng dữ liệu tiêu biểu

1. Thêm giao dịch: `AddTransactionScreen` nhập dữ liệu -> `AddTransactionViewmodel.save()` -> `CreateTransactionUseCase.invoke()` kiểm tra auth, amount, category -> `TransactionRepositoryImpl.createTransaction()` tạo `TransactionEntity` có `syncStatus = PENDING`, `pendingOperation = CREATE` -> Room insert -> `SyncTrigger.requestImmediateSync()` tạo WorkManager job.
2. Hiển thị thống kê: `HomeViewModel` lấy `GetTransactionWithCategoryUseCase()` -> combine giao dịch và danh mục -> lọc theo thời gian bằng `TimeRangeCalculator` -> cộng tổng thu/chi -> nhóm theo category bằng `groupByCategory()` -> Compose vẽ chart bằng Canvas.
3. Nhắc nhở: `ReminderViewModel.saveReminder()` -> `CreateReminderUseCase`/`UpdateReminderUseCase` -> `ReminderRepositoryImpl` lưu Room + đánh dấu pending sync -> `AlarmReminderScheduler.schedule()` đặt `AlarmManager` -> `ReminderReceiver` hiện notification và tính lần lặp tiếp theo.

## 2. Danh sách ngôn ngữ và công nghệ

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| Kotlin 2.2.20 | Ngôn ngữ chính; dùng data class, sealed class/interface, extension function, operator overloading, coroutine/Flow. | `gradle/libs.versions.toml` `kotlin = "2.2.20"`; `Money.plus/minus`, `ReminderUiState`, `CategoryIdentity.categoryIdentityKey` | Đã triển khai và sử dụng | Bắt buộc |
| Android Gradle Plugin 8.13.2 | Build Android app module. | `gradle/libs.versions.toml` `agp = "8.13.2"`; `app/build.gradle.kts` | Đã khai báo và sử dụng | Nên trình bày |
| Android SDK | `compileSdk`/`targetSdk` 36, `minSdk` 26, app version 1.0. | `app/build.gradle.kts` | Đã cấu hình | Nên trình bày |
| Java/Kotlin JVM target 11 | Compile options Java 11, Kotlin `jvmTarget = "11"`. | `app/build.gradle.kts` | Đã cấu hình | Chỉ đề cập ngắn |
| Jetpack Compose | UI chính của ứng dụng, dùng Material 3, Canvas, state observation. | `buildFeatures { compose = true }`; `HomeScreen`, `ReminderScreen`, `MorphingChartSection` | Đã triển khai và sử dụng | Bắt buộc |
| Room Database | CSDL local `moneymate.db` với entity/DAO cho users, transactions, categories, budgets, reminders. | `AppDatabase`, `TransactionEntity`, `TransactionDao`, `DatabaseModule` | Đã triển khai và sử dụng | Bắt buộc |
| Firebase Authentication | Email/password và Google credential sign-in. | `AuthRepositoryImpl.signIn`, `signUp`, `signInWithGoogle`; `FireBaseModule.provideAuth` | Đã triển khai và sử dụng | Bắt buộc |
| Cloud Firestore | Lưu dữ liệu remote theo collection con của user, chủ yếu sync pending local lên Firestore. | `TransactionRepositoryImpl.syncPendingTransactions`, `CategoryRepositoryImpl.syncPendingCategories`, `BudgetRepositoryImpl.syncPendingBudgets`, `ReminderRepositoryImpl.syncPendingReminders` | Đã triển khai và sử dụng | Bắt buộc |
| Firebase Analytics | Dependency được khai báo qua Firebase BoM, chưa thấy code gọi Analytics. | `app/build.gradle.kts` `firebase-analytics`; không thấy import/call analytics trong Kotlin | Đã khai báo dependency, chưa tìm thấy nơi sử dụng | Không cần đưa vào hoặc chỉ nêu ngắn |
| Google ML Kit Text Recognition | Dependency OCR được khai báo, nhưng không tìm thấy `TextRecognition`, `InputImage`, OCR parser, camera/gallery input hay receipt parsing trong source Kotlin. | `app/build.gradle.kts` `com.google.mlkit:text-recognition:16.0.1`; tìm kiếm source chỉ thấy icon receipt/camera, không thấy API ML Kit | Đã khai báo dependency nhưng chưa tìm thấy nơi sử dụng | Bắt buộc nêu đúng trạng thái vì đề tài có OCR |
| WorkManager | Đồng bộ nền bằng `SyncWorker`, periodic và one-time work, ràng buộc mạng. | `SyncWorker`, `SyncScheduler.schedulePeriodicSync`, `triggerImmediateSync`, `App.workManagerConfiguration` | Đã triển khai và sử dụng | Bắt buộc |
| AlarmManager + Notification | Nhắc nhở tài chính bằng exact/inexact alarm, broadcast receiver và notification. | `AlarmReminderScheduler`, `ReminderReceiver`, `ReminderNotificationManager`, `AndroidManifest.xml` permissions/receivers | Đã triển khai và sử dụng | Nên trình bày |
| AndroidX Credentials + Google ID | Lấy Google ID token bằng Credential Manager/Google ID library; ngoài ra UI auth vẫn dùng `GoogleSignInOptions` từ Play Services Auth. | `requestGoogleIdToken`, `AuthViewModel.signInWithGoogle`; `LoginScreen`, `RegisterScreen` gọi Google Sign-In intent | Đã triển khai và sử dụng | Nên trình bày |
| DataStore Preferences | Lưu `theme_mode` cho tuỳ biến giao diện. | `AppPreferences`, `PreferencesModule`, `AppRootViewModel.themeMode` | Đã triển khai và sử dụng | Nên trình bày |
| SharedPreferences/AppCompatDelegate | Đồng bộ night mode cho startup/AppCompat/XML theme. | `NightModeSync.save`, `applySavedModeOnStartup`, `CustomizationViewModel.changeTheme` | Đã triển khai và sử dụng | Chỉ đề cập ngắn |
| Localization | Có strings tiếng Việt/Anh và đổi locale bằng AppCompatDelegate. | `res/values/strings.xml`, `res/values-en/strings.xml`, `CustomizationViewModel.changeLanguage` | Đã triển khai và sử dụng | Nên trình bày |

## 3. Danh sách framework và thư viện, kèm phiên bản

### Dependency đã khai báo và nơi sử dụng

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| AndroidX Core KTX 1.18.0 | Extension AndroidX; dùng cùng Compose/AppCompat. | `gradle/libs.versions.toml`, `app/build.gradle.kts` | Đã khai báo, usage gián tiếp/chung | Chỉ đề cập ngắn |
| Lifecycle Runtime KTX 2.10.0 | Nền cho lifecycle/ViewModel coroutine. | `gradle/libs.versions.toml`; ViewModel dùng `viewModelScope` qua lifecycle-viewmodel transitive | Đã khai báo và gián tiếp sử dụng | Nên trình bày |
| Activity Compose 1.13.0 | `setContent` và Compose trong Activity. | `app/build.gradle.kts`; `MainActivity.setContent` | Đã sử dụng | Nên trình bày |
| Compose BOM 2024.09.00 | Quản lý phiên bản Compose UI/Material3/tooling. | `app/build.gradle.kts`, `gradle/libs.versions.toml` | Đã khai báo và sử dụng | Bắt buộc |
| Compose Foundation 1.10.6 | UI layout, pager/combinedClickable. | `app/build.gradle.kts`; nhiều Screen import `foundation` | Đã sử dụng | Nên trình bày |
| Compose Material3 | Thành phần UI Material 3. | `app/build.gradle.kts`; `MaterialTheme`, `Scaffold`, `TopAppBar`, `AlertDialog` trong Screen | Đã sử dụng | Bắt buộc |
| Material Icons Extended 1.6.5 | Icon UI cho drawer, button, screen. | `app/build.gradle.kts`; nhiều Screen import material icons | Đã sử dụng | Chỉ đề cập ngắn |
| Navigation Compose 2.7.7 | Điều hướng màn hình Compose. | `app/build.gradle.kts`; `NavGraph`, `Screen` | Đã sử dụng | Bắt buộc |
| Hilt Android 2.56.1 | DI toàn app. | `build.gradle.kts`, `app/build.gradle.kts`; `@HiltAndroidApp`, `@HiltViewModel`, modules | Đã sử dụng | Bắt buộc |
| Hilt Navigation Compose 1.2.0 | Lấy ViewModel qua `hiltViewModel()` trong Composable. | `app/build.gradle.kts`; `NavGraph`, `HomeScreen`, `ReminderScreen` | Đã sử dụng | Nên trình bày |
| Work Runtime KTX 2.9.0 | Worker sync nền. | `app/build.gradle.kts`; `CoroutineWorker`, WorkRequest builders | Đã sử dụng | Bắt buộc |
| Hilt Work 1.2.0 | Inject worker bằng `@HiltWorker`, `HiltWorkerFactory`. | `app/build.gradle.kts`; `App.kt`, `SyncWorker` | Đã sử dụng | Nên trình bày |
| Room Runtime/KTX/Compiler 2.8.4 | ORM local DB, Flow DAO, KSP compiler. | `app/build.gradle.kts`; `AppDatabase`, DAO/entity | Đã sử dụng | Bắt buộc |
| Firebase BoM 34.17.0 | Quản lý version Firebase Auth/Firestore/Analytics. | `app/build.gradle.kts` | Đã sử dụng | Bắt buộc |
| Firebase Auth | Email/password, Google Auth credential, auth state listener. | `AuthRepositoryImpl`, `FireBaseModule` | Đã sử dụng | Bắt buộc |
| Firebase Firestore | Remote collections và security rules. | `FireBaseModule`, repository sync, `firestore.rules` | Đã sử dụng | Bắt buộc |
| Firebase Analytics | Chỉ khai báo, chưa thấy gọi API analytics. | `app/build.gradle.kts` | Chưa tìm thấy nơi sử dụng | Không cần đưa vào |
| Play Services Auth 21.2.0 | Google Sign-In intent cũ trong `LoginScreen`/`RegisterScreen`. | `app/build.gradle.kts`; `LoginScreen`, `RegisterScreen` dùng `GoogleSignInOptions` | Đã sử dụng | Nên trình bày |
| Google ML Kit Text Recognition 16.0.1 | Dependency OCR, chưa thấy gọi API. | `app/build.gradle.kts`; không thấy `TextRecognition`/`InputImage` | Khai báo nhưng chưa sử dụng | Chỉ đề cập ngắn, nhấn mạnh chưa triển khai OCR |
| AndroidX Credentials 1.3.0 | Credential Manager lấy Google ID token/clear credential state. | `requestGoogleIdToken`, `AuthRepositoryImpl.signOut` | Đã sử dụng | Nên trình bày |
| Google ID 1.1.1 | Tạo `GetGoogleIdOption`, parse `GoogleIdTokenCredential`. | `requestGoogleIdToken` | Đã sử dụng | Nên trình bày |
| AppCompat 1.7.0 | `MainActivity` kế thừa `AppCompatActivity`; AppCompatDelegate dùng theme/locale. | `MainActivity`, `CustomizationViewModel`, `NightModeSync` | Đã sử dụng | Nên trình bày |
| DataStore Preferences 1.1.1 | Lưu theme mode. | `AppPreferences` | Đã sử dụng | Nên trình bày |
| Vico Charts | Không thấy dependency Vico trong Gradle; chart hiện vẽ bằng Compose Canvas. | `app/build.gradle.kts` không có Vico; `MorphingChartSection` dùng `Canvas` | Không sử dụng | Không cần đưa vào |
| Biometric/PIN/Encrypted storage | Không thấy dependency/API biometric, PIN, EncryptedSharedPreferences, Keystore. | Tìm kiếm source không có `Biometric`, `PIN`, `MasterKey`, `EncryptedSharedPreferences` | Chưa triển khai/chưa xác minh được | Chỉ nêu trong phần chưa đủ bằng chứng |

## 4. Danh sách thuật toán và kỹ thuật

### A. OCR và xử lý hóa đơn

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| OCR bằng ML Kit | Dependency `com.google.mlkit:text-recognition:16.0.1` tồn tại, nhưng không thấy class/function tiếp nhận ảnh, tạo `InputImage`, gọi `TextRecognition.getClient()`, hoặc xử lý kết quả OCR. | `app/build.gradle.kts`; tìm kiếm không thấy `TextRecognition`, `InputImage`, `recognize`, OCR parser | Đã khai báo dependency nhưng chưa tìm thấy nơi sử dụng | Bắt buộc, nhưng ghi là chưa triển khai trong code hiện tại |
| Tiếp nhận hình ảnh hóa đơn | Không thấy camera/gallery picker cho hóa đơn; `rememberLauncherForActivityResult` chỉ dùng cho Google Sign-In và permission reminder. | `LoginScreen`, `RegisterScreen`, `ReminderScreen`; không thấy `GetContent`, `TakePicture`, camera/gallery trong transaction/OCR | Chưa triển khai/chưa xác minh được | Bắt buộc nêu thiếu bằng chứng |
| Tiền xử lý ảnh | Không thấy resize/crop/grayscale/threshold hoặc xử lý bitmap liên quan hóa đơn. | Không có file OCR/image preprocessing trong `data`, `domain`, `presentation` | Chưa triển khai/chưa xác minh được | Chỉ đề cập ngắn |
| Trích xuất số tiền/ngày từ OCR | Không thấy parser hóa đơn, regex nhận diện tổng tiền/ngày, heuristic normalize chuỗi OCR. | Tìm kiếm `Regex` chỉ thấy `CategoryStableId.defaultFallbackStableId`; `Patterns.EMAIL_ADDRESS` cho email | Chưa triển khai/chưa xác minh được | Bắt buộc nêu chưa có |
| Logic regex hiện có | Regex duy nhất liên quan xử lý chuỗi là chuẩn hóa `iconKey` thành stable id fallback, không phải OCR. | `CategoryStableId.defaultFallbackStableId` dùng `Regex("[^a-z0-9]+")` | Đã dùng cho category id, không phải OCR | Chỉ đề cập ngắn |

Kết luận OCR: ở trạng thái repository hiện tại, đề tài có nhắc OCR qua dependency ML Kit nhưng chưa có bằng chứng code cho tính năng quét hóa đơn. Khi viết Chương 1, chỉ nên trình bày OCR/ML Kit như nền tảng lý thuyết định hướng cho tính năng cần tích hợp, đồng thời báo cáo tiến độ hiện tại là chưa thấy triển khai.

### B. Đồng bộ dữ liệu Offline-First

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| Lưu local trước | Tạo/sửa/xóa giao dịch, category, budget, reminder đều ghi Room và đánh dấu pending trước khi sync. | `TransactionRepositoryImpl.createTransaction/update/deleteTransaction`, `CategoryRepositoryImpl.createCategory/update/archiveCategory`, `BudgetRepositoryImpl.setBudget/deleteBudget`, `ReminderRepositoryImpl.create/update/delete` | Đã triển khai và sử dụng | Bắt buộc |
| Trạng thái đồng bộ | Entity có `SyncStatus`, `PendingOperation`, `lastSyncError`, `retryCount`, `lastSyncAttemptAt`, `remoteUpdatedAt`. | `TransactionEntity`, `CategoryEntity`, `BudgetEntity`, `ReminderEntity` | Đã thiết kế trong model local | Bắt buộc |
| Phát hiện dữ liệu cần sync | DAO lấy các record `pendingOperation != 'NONE'`. | `TransactionDao.getPendingTransactionsForSync`, `CategoryDao.getPendingCategoriesForSync`, `BudgetDao.getPendingBudgetsForSync`, `ReminderDao.getPendingRemindersForSync` | Đã triển khai | Bắt buộc |
| Đẩy dữ liệu lên Firestore | Repositories dùng `firestore.collection("users").document(userId).collection(...).document(localId).set/delete`. | `TransactionRepositoryImpl.syncPendingTransactions`, `CategoryRepositoryImpl.syncPendingCategories`, `BudgetRepositoryImpl.syncPendingBudgets`, `ReminderRepositoryImpl.syncPendingReminders` | Đã triển khai | Bắt buộc |
| Kéo dữ liệu từ Firestore | DTO có `toEntity` và DAO có `upsertFromRemote`, nhưng tìm kiếm không thấy nơi gọi `toEntity`, `upsertFromRemote`, `get().await()`, `addSnapshotListener`. | `data/remote/dto/*`, DAO `upsertFromRemote`; không thấy usage trong repository/worker | Đang thiết kế/chưa hoàn thiện | Bắt buộc nêu rõ giới hạn |
| Xử lý xung đột | Không thấy logic so sánh `remoteUpdatedAt`/`updatedAt` giữa local và remote hoặc policy conflict cụ thể. | Có field timestamp trong entity/DTO, nhưng không có conflict resolver | Chưa triển khai/chưa xác minh được | Bắt buộc nêu thiếu bằng chứng |
| Retry WorkManager | `SyncWorker` trả `Result.retry()` nếu exception và `runAttemptCount < MAX_ATTEMPTS`; WorkRequest có exponential backoff. | `SyncWorker.doWork`, `SyncScheduler.schedulePeriodicSync` | Đã triển khai ở cấp worker | Bắt buộc |
| Điều kiện mạng | Periodic/one-time sync yêu cầu `NetworkType.CONNECTED`; periodic còn yêu cầu battery not low. | `SyncScheduler.schedulePeriodicSync`, `triggerImmediateSync` | Đã triển khai | Nên trình bày |
| Tần suất sync định kỳ | WorkManager periodic 30 phút, one-time sync khi có thay đổi local. | `PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)`, `SyncTrigger.requestImmediateSync` | Đã triển khai | Nên trình bày |
| Xử lý lỗi từng entity | Transaction lỗi gọi `markSyncFailed`; budget chỉ `println`; reminder catch có comment cần update retry; category catch bỏ qua và chưa mark synced. | `TransactionRepositoryImpl.syncPendingTransactions`, `BudgetRepositoryImpl.syncPendingBudgets`, `ReminderRepositoryImpl.syncPendingReminders`, `CategoryRepositoryImpl.syncPendingCategories` | Chưa đồng nhất/chưa hoàn thiện | Bắt buộc nêu giới hạn |
| Category sync mark synced | `CategoryRepositoryImpl.syncPendingCategories` set Firestore nhưng không gọi `dao.markSynced`, nên pending có thể còn tồn tại sau sync thành công. | `CategoryRepositoryImpl.syncPendingCategories`; `CategoryDao.markSynced` tồn tại nhưng không được gọi | Chưa hoàn thiện/có rủi ro logic | Bắt buộc |

Tên kỹ thuật phù hợp: Offline-first local write with pending-operation queue, background sync with WorkManager, Firestore per-user subcollections, retry with exponential backoff. Không nên mô tả là đồng bộ hai chiều hoàn chỉnh vì chưa có bằng chứng pull/conflict resolution.

### C. Xử lý giao dịch, ngân sách và thống kê

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| Tính tổng thu/chi | Lọc giao dịch theo khoảng thời gian, cộng `amountMinor` theo `TransactionType.INCOME/EXPENSE`. | `HomeViewModel.recompute` | Đã triển khai và sử dụng | Bắt buộc |
| Tính số dư | Tổng toàn bộ giao dịch: income cộng, expense trừ. | `HomeViewModel.recompute` cập nhật `_totalBalance` | Đã triển khai và sử dụng | Bắt buộc |
| Lọc theo thời gian | Tính khoảng ngày/tuần/tháng/năm/custom bằng `Calendar`, lọc `createdAt in start until end`. | `TimeRangeCalculator.getTimeRange`, `HomeViewModel.recompute`, `HistoryViewModel.recompute` | Đã triển khai và sử dụng | Bắt buộc |
| Điều hướng thời gian | Dịch anchor theo day/week/month/year bằng `Calendar.add`. | `TimeRangeCalculator.moveAnchor`, `HomeViewModel.moveTimeRange`, `HistoryViewModel.moveTimeRange` | Đã triển khai | Nên trình bày |
| Nhóm theo danh mục | Lọc type, bỏ transaction không có category, group theo `stableId ?: id`, cộng tổng, đếm số lượng, lấy latest transaction. | `TransactionGrouping.groupByCategory`, `CategoryIdentity.categoryIdentityKey` | Đã triển khai và sử dụng | Bắt buộc |
| Sắp xếp thống kê | Chart group mặc định sort theo tổng giảm dần; History sort theo ngày mới nhất hoặc số tiền giảm dần. | `groupByCategory.sortedByDescending(totalAmount)`, `HistoryViewModel.DetailSortType` | Đã triển khai | Nên trình bày |
| Biểu đồ custom Canvas | Chuyển dữ liệu category thành segment percentage; vẽ morph pie-to-bar bằng `Canvas`, `drawArc`, `drawRoundRect`, nội suy `lerp`, phân bổ minimum size. | `MorphingChartSection`, `MorphingCanvas`, `allocateWithMinimum`, `lerp` | Đã triển khai và sử dụng | Nên trình bày |
| Format tiền | Format compact nghìn/triệu/tỷ và full với dấu chấm nhóm nghìn. | `CurrencyFormatter.formatCompact`, `formatFull` | Đã triển khai | Nên trình bày |
| Nhập số tiền | UI chỉ giữ chữ số, visual transformation thêm dấu phân tách hàng nghìn. | `AddTransactionViewmodel.onAmountChange`, `AddTransactionScreen.thousandSeparatorTransformation` | Đã triển khai | Nên trình bày |
| Budget effective-dated | Lấy budget mới nhất theo category trước hoặc bằng tháng đang xem bằng SQL `MAX(effectiveYear*100+effectiveMonth)`. | `BudgetDao.getActiveBudgetsForMonth` | Đã triển khai | Bắt buộc |
| Tính budget overview | Combine budget và transaction tháng, group chi tiêu theo category, tính totalBudget, totalSpent, totalRemaining. | `GetMonthlyBudgetOverviewUseCase.invoke` | Đã triển khai và sử dụng | Bắt buộc |
| Chỉ sửa budget tháng hiện tại | UI/ViewModel kiểm tra tháng đang xem trùng tháng hiện tại trước khi save/delete. | `BudgetViewModel.canModifyBudget`, `saveBudget`, `deleteBudget` | Đã triển khai | Nên trình bày |
| Xóa danh mục an toàn | Trước khi archive category, reassign transaction sang category thay thế cùng loại. | `DeleteCategoryUseCase`, `TransactionDao.reassignCategory`, `CategoryDao.archiveCategory` | Đã triển khai | Nên trình bày |
| Giao dịch định kỳ | Có `RecurringTransactionEntity` và route `RecurringTransactions`, nhưng entity không nằm trong `AppDatabase`, không có DAO/repository/usecase/screen tương ứng. | `RecurringTransactionEntity.kt`, `Screen.RecurringTransactions`; `AppDatabase` không khai báo entity này | Chưa hoàn thiện/chưa triển khai | Chỉ đề cập ngắn |
| Tìm kiếm giao dịch | Không thấy chức năng search text/full-text. | Không có search query/UI trong source được đọc | Chưa xác minh được | Không cần đưa vào |

### D. Bảo mật, xác thực và xử lý nền

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| Xác thực email/password | Firebase Auth login/register; map exception sang `AuthError`. | `AuthRepositoryImpl.signIn`, `signUp`, `toAuthError`; `SignInUseCase`, `SignUpUseCase` | Đã triển khai | Bắt buộc |
| Đăng nhập Google | Có 2 đường: Credential Manager helper và Google Sign-In intent ở UI, đều đưa id token vào `SignInWithGoogleUseCase`. | `requestGoogleIdToken`, `AuthViewModel.signInWithGoogleToken`, `LoginScreen`, `RegisterScreen`, `AuthRepositoryImpl.signInWithGoogle` | Đã triển khai | Nên trình bày |
| Theo dõi auth state | Firebase `AuthStateListener` được chuyển thành Flow bằng `callbackFlow`; nhiều ViewModel dùng `flatMapLatest` theo user. | `AuthRepositoryImpl.observeAuthState`, `GetTransactionWithCategoryUseCase`, `ReminderViewModel`, `BudgetViewModel` | Đã triển khai | Bắt buộc |
| Security rules Firestore | Chỉ cho user đọc/ghi document của chính mình và validate schema cơ bản. | `firestore.rules` | Đã có cấu hình | Nên trình bày |
| Lưu mật khẩu | Không thấy lưu mật khẩu local; password chỉ giữ trong state UI và gửi Firebase Auth. | `LoginScreen`, `RegisterScreen`, `AuthRepositoryImpl.signIn/signUp` | Không lưu local theo bằng chứng hiện tại | Nên trình bày |
| PIN/sinh trắc học | Không thấy BiometricPrompt, PIN, encrypted preferences, Android Keystore. | Tìm kiếm không thấy `Biometric`, `PIN`, `MasterKey`, `EncryptedSharedPreferences` | Chưa triển khai/chưa xác minh được | Chỉ đề cập ngắn |
| Reminder background | AlarmManager đặt alarm, BroadcastReceiver xử lý, notification channel, permission POST_NOTIFICATIONS. | `AlarmReminderScheduler`, `ReminderReceiver`, `ReminderNotificationManager`, `AndroidManifest.xml` | Đã triển khai | Nên trình bày |
| Reschedule sau boot/permission | Receiver lắng nghe `BOOT_COMPLETED` và `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, gọi `RescheduleRemindersUseCase`. | `ReminderBootReceiver`, `AndroidManifest.xml` | Đã triển khai | Nên trình bày |
| Quản lý alarm theo phiên đăng nhập | Khi user đổi/logout, hủy alarm user cũ; khi user mới login, reschedule reminders. | `ReminderAuthSessionManager.start`, `cancelUserAlarms` | Đã triển khai | Nên trình bày |
| Backup rules | File backup/data extraction còn mẫu, chưa có include/exclude cụ thể. | `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | Chưa tùy biến/chưa hoàn thiện | Chỉ đề cập ngắn |

## 5. Các lý thuyết nền tảng liên quan

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| Lập trình hướng đối tượng | Class, interface, data class, sealed class; repository/usecase/model. | Toàn bộ Kotlin source; ví dụ `ReminderScheduler`, `Money`, `AuthError` | Có liên quan | Nên trình bày |
| MVVM | ViewModel độc lập UI, Screen Compose quan sát state. | `HomeViewModel` + `HomeScreen`; `ReminderViewModel` + `ReminderScreen` | Có liên quan trực tiếp | Bắt buộc |
| Clean Architecture | Domain usecase/repository interface tách data implementation; cần nêu là áp dụng một phần. | `domain/usecase`, `domain/repository`, `data/repository`, `core/di/RepositoryModule` | Có liên quan trực tiếp | Bắt buộc |
| Repository Pattern | Che giấu Room/Firestore sau interface domain. | `TransactionRepository`, `TransactionRepositoryImpl` | Có liên quan trực tiếp | Bắt buộc |
| Dependency Injection | Hilt quản lý object graph và worker injection. | `@HiltAndroidApp`, `@Module`, `@HiltWorker` | Có liên quan trực tiếp | Bắt buộc |
| Reactive Programming | Flow/StateFlow/callbackFlow/combine/flatMapLatest/stateIn để phản ứng dữ liệu và auth state. | `GetTransactionWithCategoryUseCase`, `BudgetViewModel`, `AuthRepositoryImpl` | Có liên quan trực tiếp | Bắt buộc |
| Coroutines và bất đồng bộ | `viewModelScope.launch`, `suspend`, `Dispatchers.Default/IO`, `await()` Firebase tasks. | ViewModel, repository, receivers, worker | Có liên quan trực tiếp | Bắt buộc |
| Offline-first | Local Room là nguồn hiển thị; pending operation queue đẩy lên Firestore; chưa đủ bằng chứng đồng bộ hai chiều. | Entity sync fields, DAO pending queries, `SyncWorker` | Có liên quan nhưng cần nêu giới hạn | Bắt buộc |
| CSDL quan hệ | Room/SQLite entity, primary key, indices, SQL query, unique index budget. | `TransactionEntity`, `BudgetEntity`, DAO queries | Có liên quan trực tiếp | Bắt buộc |
| NoSQL/Firestore | Document/subcollection theo user, security rules. | Repository sync; `firestore.rules` | Có liên quan trực tiếp | Bắt buộc |
| OCR | Liên quan đề tài và dependency, nhưng code hiện chưa dùng. | `app/build.gradle.kts` ML Kit dependency; không có source OCR | Lý thuyết định hướng, chưa có triển khai | Bắt buộc nêu đúng trạng thái |
| Quản lý trạng thái UI | Sealed UI state, StateFlow, event channel, Compose state. | `HomeUiState`, `ReminderUiState`, `AddTransactionEvent`, `collectAsState` | Có liên quan trực tiếp | Bắt buộc |
| Lập lịch nền | WorkManager cho sync, AlarmManager cho reminder. | `SyncScheduler`, `AlarmReminderScheduler` | Có liên quan | Nên trình bày |

## 6. Tiêu chuẩn và quy trình thực tế

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| Tổ chức source code theo package | Package rõ theo `core`, `data`, `domain`, `presentation`, `navigation`, `ui`. | Cấu trúc thư mục `app/src/main/java/com/example/moneymatev2` | Thực hành kỹ thuật thực tế | Nên trình bày |
| Quản lý dependency bằng Gradle Kotlin DSL + version catalog | Dùng `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`; một số dependency vẫn khai báo trực tiếp trong app Gradle. | Các file Gradle | Thực hành kỹ thuật thực tế | Nên trình bày |
| Quản lý phiên bản bằng Git | Repo có `.git`, lịch sử commit; `git log` gần nhất có commit về reminder/settings/UI. | `.git` tồn tại; `git log --oneline -5` | Có bằng chứng sử dụng Git | Chỉ đề cập ngắn |
| CI/CD | Không thấy thư mục `.github` hoặc workflow CI trong repo hiện tại. | `Get-ChildItem .github` không có kết quả | Chưa có bằng chứng | Không cần đưa vào hoặc nêu chưa có |
| Test tự động | Chỉ có test mẫu mặc định `addition_isCorrect` và `useAppContext`, chưa kiểm thử logic nghiệp vụ. | `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt` | Có khung test nhưng chưa có test nghiệp vụ | Chỉ đề cập ngắn |
| Security rules Firestore | Có rules phân quyền theo `request.auth.uid == userId` và validate field cơ bản. | `firestore.rules` | Thực hành bảo mật backend | Nên trình bày |
| Phân quyền Android | Manifest khai báo notification, exact alarm, boot completed; UI request permission notification/exact alarm. | `AndroidManifest.xml`, `ReminderScreen`, `ReminderNotificationManager` | Đã triển khai | Nên trình bày |
| Backup/data extraction | File còn template, chưa cấu hình exclude/include cụ thể cho dữ liệu nhạy cảm. | `backup_rules.xml`, `data_extraction_rules.xml` | Chưa hoàn thiện | Chỉ đề cập ngắn |
| ProGuard/R8 | Release `isMinifyEnabled = false`, `proguard-rules.pro` mặc định. | `app/build.gradle.kts`, `app/proguard-rules.pro` | Chưa bật minify/obfuscation | Chỉ đề cập ngắn |
| Quy trình Agile/Scrum/SOLID/OWASP/ISO | Không có tài liệu/quy trình chính thức trong repo chứng minh áp dụng. | README chỉ có tiêu đề; không có docs quy trình | Chưa xác minh được | Không tự khẳng định |
| Rà soát/debug lỗi | Có comment và mapping error trong code, nhưng không có quy trình review/debug chính thức. | `AuthErrorMapper`, `TransactionErrorMapper`, comments trong ViewModel/repository | Thực hành code, không phải quy trình chính thức | Chỉ đề cập ngắn |
| Quản lý secrets | `app/.gitignore` có `google-services.json` nhưng `git ls-files app/google-services.json` cho thấy file đang được theo dõi bởi Git. Không đọc/nêu nội dung file. | `app/.gitignore`, kết quả `git ls-files` | Có rủi ro quản lý cấu hình nhạy cảm | Nên nêu ở phần rủi ro/bảo mật, không trích nội dung |

## 7. Các chức năng đã triển khai và trạng thái

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| Đăng ký/đăng nhập email-password | Firebase Auth, validate email/password, seed default categories sau login/register. | `SignUpUseCase`, `SignInUseCase`, `AuthRepositoryImpl`, `CategoryRepositoryImpl.seedDefaultCategoriesIfNeeded` | Đã triển khai | Nên trình bày |
| Đăng nhập Google | Google Sign-In/Credential token -> Firebase credential. | `LoginScreen`, `RegisterScreen`, `requestGoogleIdToken`, `SignInWithGoogleUseCase` | Đã triển khai | Nên trình bày |
| Đăng xuất | `DrawerViewModel.logout` gọi `authRepository.signOut`; `SignOutUseCase` gọi thêm `userRepository.signOut` nhưng `UserRepositoryImpl.signOut` còn TODO. | `DrawerViewModel`, `AuthRepositoryImpl.signOut`, `SignOutUseCase`, `UserRepositoryImpl.signOut` | Đã triển khai một phần; có TODO | Chỉ đề cập ngắn |
| Thêm giao dịch | Nhập loại, số tiền, danh mục, ghi chú, ngày; lưu Room, pending sync. | `AddTransactionScreen`, `AddTransactionViewmodel`, `CreateTransactionUseCase`, `TransactionRepositoryImpl.createTransaction` | Đã triển khai | Bắt buộc |
| Xem dashboard thu/chi/số dư | Lọc theo kỳ, tính tổng, chart theo category. | `HomeViewModel`, `HomeScreen`, `MorphingChartSection` | Đã triển khai | Bắt buộc |
| Lịch sử/chi tiết giao dịch | Lọc theo kỳ/type, sort theo date/amount, xem group/category transaction. | `HistoryViewModel`, `HistoryScreen` | Đã triển khai | Nên trình bày |
| Quản lý danh mục | Xem danh mục thu/chi, thêm danh mục, archive danh mục và reassign transaction. | `CategoryManagementViewModel`, `AddCategoryViewModel`, `DeleteCategoryUseCase` | Đã triển khai | Nên trình bày |
| Ngân sách tháng | Đặt/xóa budget tháng hiện tại, xem tiến độ và remaining, lấy effective budget theo tháng. | `BudgetViewModel`, `BudgetRepositoryImpl`, `BudgetDao`, `GetMonthlyBudgetOverviewUseCase` | Đã triển khai | Bắt buộc |
| Nhắc nhở | CRUD reminder, repeat daily/weekly/monthly, notification, reschedule boot/auth change. | `ReminderViewModel`, `ReminderRepositoryImpl`, `ReminderRepeatCalculator`, `AlarmReminderScheduler`, `ReminderReceiver`, `ReminderBootReceiver` | Đã triển khai | Nên trình bày |
| Đồng bộ cloud | Local pending -> Firestore bằng WorkManager; chưa thấy pull remote/conflict handling đầy đủ. | `SyncWorker`, `SyncManager`, repository sync methods | Đã triển khai một phần | Bắt buộc |
| Tùy chỉnh theme/ngôn ngữ | Theme light/dark/system bằng DataStore + AppCompat; ngôn ngữ vi/en. | `CustomizationViewModel`, `AppPreferences`, `NightModeSync`, `strings.xml`, `strings-en.xml` | Đã triển khai | Nên trình bày |
| OCR hóa đơn | Chưa thấy màn hình/logic OCR, receipt parsing, camera/gallery. | Chỉ có dependency ML Kit trong Gradle | Chưa triển khai/chưa xác minh được | Bắt buộc nêu thiếu so với đề tài |
| Giao dịch định kỳ | Có model/route rời rạc, không có DB/DAO/repository/UI hoàn chỉnh. | `RecurringTransactionEntity`, `Screen.RecurringTransactions`, không có trong `AppDatabase` | Đang triển khai/chưa hoàn thiện | Chỉ đề cập ngắn |
| Bảo mật PIN/sinh trắc | Không thấy code/API tương ứng. | Không có Biometric/PIN/Encrypted storage usage | Chưa triển khai/chưa xác minh được | Chỉ đề cập ngắn |

## 8. Đối chiếu với đề cương đồ án

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| Ứng dụng quản lý tài chính cá nhân | Có giao dịch thu/chi, danh mục, ngân sách, thống kê, lịch sử, nhắc nhở. | `presentation/transaction`, `home`, `budget`, `category`, `reminder` | Phù hợp đề cương | Bắt buộc |
| Android/Kotlin | App Android Kotlin dùng Gradle Kotlin DSL. | `app/build.gradle.kts`, toàn bộ Kotlin source | Phù hợp đề cương | Bắt buộc |
| Kiến trúc hiện đại | MVVM + repository/usecase + Hilt + Room/Firestore. | `presentation`, `domain`, `data`, `core/di` | Phù hợp, nhưng Clean Architecture chưa tuyệt đối | Bắt buộc |
| Offline-first | Có local Room + pending sync + WorkManager, nhưng chủ yếu push local lên Firestore; chưa thấy pull remote/conflict. | Entity sync fields, DAO pending, `SyncWorker` | Phù hợp một phần | Bắt buộc, trình bày giới hạn |
| OCR hóa đơn bằng ML Kit | Chỉ thấy dependency ML Kit; chưa có code OCR/receipt extraction. | `app/build.gradle.kts`; không thấy API ML Kit trong Kotlin | Chưa đáp ứng trong code hiện tại | Bắt buộc nêu là khoảng trống |
| Firebase Authentication/Firestore | Auth và Firestore đã dùng thực tế. | `AuthRepositoryImpl`, `FireBaseModule`, repository sync, `firestore.rules` | Phù hợp đề cương | Bắt buộc |
| Thống kê/biểu đồ | Có tổng hợp theo kỳ/category và chart Canvas custom; không dùng Vico. | `HomeViewModel`, `TransactionGrouping`, `MorphingChartSection` | Phù hợp | Nên trình bày |
| Bảo mật ứng dụng | Có Firebase Auth và Firestore rules; chưa có PIN/biometric/encrypted local storage. | `AuthRepositoryImpl`, `firestore.rules`; không có biometric/PIN API | Phù hợp một phần | Nên trình bày |

## 9. Đề xuất nội dung cho từng mục Chương 1

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| 1.1 Tổng quan bài toán quản lý tài chính cá nhân | Trình bày nhu cầu theo dõi thu/chi, danh mục, ngân sách, lịch sử, nhắc nhở. | Các package `transaction`, `home`, `budget`, `category`, `reminder` | Có bằng chứng mạnh | Bắt buộc |
| 1.2 Nền tảng Android và Kotlin | Android SDK, Kotlin, Gradle, coroutine, Flow, Compose. | `app/build.gradle.kts`, `gradle/libs.versions.toml`, ViewModel/Flow usage | Có bằng chứng mạnh | Bắt buộc |
| 1.3 Kiến trúc MVVM và Clean Architecture | Giải thích MVVM, usecase, repository, DI; nêu MoneyMate áp dụng thực dụng và còn phụ thuộc enum data trong domain. | `presentation/*ViewModel`, `domain/usecase`, `domain/repository`, `data/repository` | Có bằng chứng mạnh | Bắt buộc |
| 1.4 Repository Pattern và DI bằng Hilt | Repository interface/impl, module binding, injection vào ViewModel/Worker/Receiver. | `RepositoryModule`, `DatabaseModule`, `FireBaseModule`, `ReminderModule` | Có bằng chứng mạnh | Bắt buộc |
| 1.5 Lưu trữ dữ liệu local bằng Room | Entity, DAO, TypeConverter, indices, Flow query, Room database. | `AppDatabase`, `entity/*`, `dao/*`, `Converters` | Có bằng chứng mạnh | Bắt buộc |
| 1.6 Firebase Auth và Firestore | Auth state, email/password, Google sign-in, Firestore subcollections, rules. | `AuthRepositoryImpl`, `requestGoogleIdToken`, repository sync, `firestore.rules` | Có bằng chứng mạnh | Bắt buộc |
| 1.7 Offline-first và đồng bộ nền | Local write + pending queue + WorkManager constraints/retry; nêu chưa có pull/conflict đầy đủ. | Entity sync fields, DAO pending, `SyncScheduler`, `SyncWorker`, `SyncManager` | Có bằng chứng một phần | Bắt buộc |
| 1.8 Xử lý thống kê tài chính | Tính tổng thu/chi/số dư, group category, filter thời gian, budget overview. | `HomeViewModel`, `TimeRangeCalculator`, `TransactionGrouping`, `GetMonthlyBudgetOverviewUseCase` | Có bằng chứng mạnh | Bắt buộc |
| 1.9 OCR và ML Kit | Trình bày lý thuyết OCR, ML Kit Text Recognition và pipeline dự kiến; ghi rõ trong code hiện tại mới khai báo dependency, chưa thấy triển khai. | `app/build.gradle.kts` ML Kit dependency; không có usage API | Chưa triển khai trong code | Bắt buộc nhưng phải thận trọng |
| 1.10 Nhắc nhở và xử lý nền Android | AlarmManager, BroadcastReceiver, notification permission/channel, boot receiver. | `AlarmReminderScheduler`, `ReminderReceiver`, `ReminderNotificationManager`, `ReminderBootReceiver`, Manifest | Có bằng chứng mạnh | Nên trình bày |
| 1.11 Bảo mật và phân quyền | Firebase Auth, Firestore rules, Android permissions, không lưu password local theo bằng chứng; chưa có PIN/biometric. | `AuthRepositoryImpl`, `firestore.rules`, `AndroidManifest.xml` | Có bằng chứng một phần | Nên trình bày |
| 1.12 Kiểm thử và quy trình phát triển | Nêu Gradle/Git/test mẫu; chưa có CI/CD hay test nghiệp vụ. Không khẳng định Agile/Scrum/ISO/SOLID chính thức. | `.git`, `ExampleUnitTest`, `ExampleInstrumentedTest`, không có `.github` | Có bằng chứng hạn chế | Chỉ đề cập ngắn |

## 10. Những thông tin chưa đủ bằng chứng

| Nội dung | Mô tả | Bằng chứng source code | Trạng thái | Mức độ cần đưa vào Chương 1 |
|---|---|---|---|---|
| OCR hóa đơn hoạt động end-to-end | Chưa thấy camera/gallery input, ML Kit API call, text recognition, parser hóa đơn. | Không có `TextRecognition`, `InputImage`, `GetContent`, `TakePicture`, parser receipt | Chưa xác minh được/chưa triển khai | Bắt buộc nêu thiếu |
| Thuật toán OCR cụ thể bên trong ML Kit | Không thể khẳng định thuật toán nội bộ ML Kit dùng CNN/Transformer/CTC... nếu source/tài liệu chính thức không chứng minh trong repo. | Chỉ có dependency ML Kit | Chưa xác minh được | Không tự khẳng định |
| Đồng bộ hai chiều | DTO/DAO có hàm hỗ trợ pull, nhưng không thấy code gọi Firestore read/listener/upsert. | `toEntity`, `upsertFromRemote` không được gọi | Chưa hoàn thiện | Bắt buộc nêu giới hạn |
| Conflict resolution | Chưa có policy xử lý khi local/remote cùng thay đổi. | Không thấy resolver/so sánh timestamp | Chưa xác minh được | Chỉ đề cập ngắn |
| PIN/sinh trắc học | Có route `SecuritySettings` nhưng không có composable/nav destination/API biometric. | `Screen.SecuritySettings`; không thấy Biometric/PIN code | Chưa triển khai | Chỉ đề cập ngắn |
| Giao dịch định kỳ | Có model/route nhưng không tham gia database/usecase/UI hoàn chỉnh. | `RecurringTransactionEntity`, `Screen.RecurringTransactions`, không có trong `AppDatabase` | Đang triển khai/chưa hoàn thiện | Chỉ đề cập ngắn |
| CI/CD | Không thấy workflow. | Không có `.github` | Chưa có bằng chứng | Không cần đưa vào |
| Test nghiệp vụ | Chỉ có test mẫu Android Studio. | `ExampleUnitTest`, `ExampleInstrumentedTest` | Chưa triển khai | Chỉ đề cập ngắn |
| Quy trình Agile/Scrum/ISO/OWASP chính thức | Không có tài liệu trong repo. | README chỉ có tiêu đề, không có docs quy trình | Chưa xác minh được | Không tự khẳng định |
| Firebase Analytics | Dependency có nhưng không thấy event tracking. | `firebase-analytics` trong Gradle, không có usage Kotlin | Khai báo chưa sử dụng | Không cần đưa vào |
| Vico Charts | Không có dependency/usage. Chart là Compose Canvas custom. | `app/build.gradle.kts`, `MorphingChartSection` | Không sử dụng | Không cần đưa vào |
| Secret/config hygiene | `google-services.json` tồn tại và được Git liệt kê; báo cáo không đọc nội dung. | `app/.gitignore`, `git ls-files app/google-services.json` | Có rủi ro cần rà soát riêng | Nên nêu trong bảo mật nếu phù hợp |

## Từ khóa tìm kiếm tài liệu khoa học và tài liệu kỹ thuật chính thức

| Chủ đề | Từ khóa gợi ý |
|---|---|
| Android/Kotlin | `Android app architecture Kotlin official guide`, `Kotlin coroutines Flow Android ViewModel`, `Android Jetpack Compose state management` |
| MVVM/Clean Architecture | `MVVM Android architecture`, `Clean Architecture Android repository use case`, `separation of concerns Android architecture` |
| Repository Pattern/DI | `Repository Pattern Android official`, `Hilt dependency injection Android official`, `Dagger Hilt ViewModel WorkManager` |
| Room Database | `Room persistence library official documentation`, `SQLite indexing Android Room`, `Room Flow DAO reactive queries` |
| Firebase Auth | `Firebase Authentication Android email password official`, `Firebase Authentication Google Sign-In Android`, `Android Credential Manager Google ID official` |
| Cloud Firestore | `Cloud Firestore Android official documentation`, `Firestore security rules per user data`, `Firestore ServerTimestamp Android` |
| Offline-first | `offline first mobile app architecture`, `Android offline first data layer`, `WorkManager data synchronization retry backoff`, `eventual consistency mobile sync` |
| WorkManager | `Android WorkManager constraints retry backoff official`, `Hilt WorkManager CoroutineWorker` |
| OCR/ML Kit | `ML Kit Text Recognition Android official`, `receipt OCR information extraction`, `OCR text recognition mobile applications`, `regular expression receipt parsing`, `information extraction from scanned receipts` |
| Thống kê tài chính | `personal finance app expense categorization`, `budget tracking algorithm`, `time series grouping financial transactions`, `data aggregation by category and date` |
| Compose Canvas Chart | `Jetpack Compose Canvas custom chart`, `data visualization pie chart stacked bar mobile UI`, `minimum segment allocation chart visualization` |
| Alarm/Notification | `Android AlarmManager exact alarm official`, `Android notification runtime permission POST_NOTIFICATIONS`, `BroadcastReceiver boot completed reminders` |
| Bảo mật | `Firebase security rules best practices`, `Android data security SharedPreferences DataStore`, `Android app backup rules sensitive data`, `Android biometric authentication official` |
| Kiểm thử | `Android unit testing ViewModel coroutine Flow`, `Compose UI testing official`, `Room database testing Android` |

## Kết luận ngắn cho Chương 1

MoneyMate v2 hiện có nền tảng kỹ thuật rõ cho ứng dụng quản lý tài chính cá nhân: Kotlin, Jetpack Compose, MVVM, Hilt, Room, Firebase Auth/Firestore, WorkManager, Flow/Coroutines, thống kê thu chi/ngân sách và nhắc nhở nền. Điểm cần trình bày thận trọng nhất là OCR: dependency ML Kit đã khai báo nhưng chưa có bằng chứng triển khai pipeline quét hóa đơn trong source code hiện tại. Vì vậy Chương 1 có thể đưa OCR/ML Kit vào cơ sở lý thuyết của đề tài, nhưng khi mô tả hiện trạng MoneyMate v2 cần ghi rõ tính năng OCR chưa được xác minh trong code.
