# P1-A FINAL: Room v3 va khoa danh muc theo tai khoan

## Pham vi da trien khai

Chi thay primary key bang `categories`: `localId` thanh `(userId, localId)`.
Khong doi ID danh muc, `stableId`, tham chieu `categoryLocalId`, Firestore document ID,
payload DTO, Rules, hoac primary key cac bang khac. Khong them unique `(userId, stableId)`.
Khong merge/deduplicate cac row trung stableId/ten. Khong tu bat lai recurring inactive.

Da duoc phe duyet sua source va test. Chua duoc phe duyet cai APK nay len hai may chinh
hoac chay migration tren du lieu that. Build APK khong tu cai dat hay mo database tren may.

## Can cu schema va migration

- Commit v2: `c936a78` (`feat(recurring): add recurring transaction flow`). Truoc khi sua,
  entity, AppDatabase va migration trong worktree khop HEAD; thay doi P0/SAFE o DAO
  khong thay schema. Room 2.8.4 sinh schema v2 truoc khi doi entity.
- `app/schemas/com.example.moneymatev2.data.local.AppDatabase/2.json` la schema duoc
  export tu source v2, identity hash `580c9c39a4c22c874a7ff3cb0463a5a3`.
- `3.json` la schema do compiler sinh sau thay doi. Doi chieu JSON xac nhan ca sau
  bang giu nguyen danh sach cot, affinity, nullability va indexes. Chi khoa categories doi.
- Commit v1: `32c3a4f`. AppDatabase v1 co nam bang; entity cua nam bang do khong doi
  so voi v2. `MIGRATION_1_2` them `recurring_transactions` va index cua bang nay.
  Fixture v1 dung schema nam bang nay, khong gia mao identity hash v1.
- `MIGRATION_2_3` tao `categories_v3`, `INSERT INTO ... SELECT ...` voi du 17 cot
  tuong minh, thay bang cu, tai tao bon index khong unique. Khong REPLACE/IGNORE
  trong migration; khong fallback destructive. Room thuc hien trong transaction va
  validate schema truoc khi hoan tat. DatabaseModule dang ky ca 1->2 va 2->3.
- Khong sua `transactions`, `budgets`, `reminders`, `recurring_transactions`, `users`.
  Khong co foreign key Room can rebuild cac bang tham chieu. Quan he duoc giai bang
  `(row.userId, row.categoryLocalId)` o repository/use case.
- Mapping ID cu -> moi la identity mapping: moi chuoi ID giu nguyen. Migration khong
  chay upload, seed, repair hay danh dau lai syncStatus/pendingOperation.

## Truy cap theo chu so huu

- CategoryDao lookup Flow/once, archive va sync query deu co UID; `@Update` dung PK kep.
  CloudPull va uploader doc snapshot theo UID + ID. PendingUploadRunner va cac guard
  full-snapshot/updatedAt/pendingOperation tu SAFE van duoc giu.
- CategoryModel mang userId; create/update khong suy ra owner tu ID. DeleteCategoryUseCase
  kiem tra owner, ton tai va loai cua source/replacement truoc thao tac hien co.
  Delete van gan lai transactions theo logic cu; migration khong goi use case nay.
- TransactionRepository getById/delete API nhan UID. Create/reassign xac minh category
  dung owner; update tu choi row thuoc owner khac. Budget set kiem tra category active
  cua dung UID. Recurring save/postDue lookup category theo UID.
- Callback category, transaction, budget, recurring mang owner tu du lieu/form da render.
  Navigation result truyen ca UID va category ID. Callback cu bi tu choi khi owner da doi.
  Form tao transaction/category va recurring/budget dialog reset theo tai khoan.
- Transaction history lay ca category archived. Orphan van xuat hien qua placeholder
  chi dung hien thi, khong persist/reassign. Budget khong mapNotNull bo row khi mat category.
  Selector tao moi chi hien active; recurring inactive khong tu duoc khoi phuc.
- Xoa DTO Room `data/local/entity/TransactionWithCategory.kt` khong co caller, vi
  `@Relation` chi theo localId se sai voi PK kep. Domain TransactionWithCategory van con.
- Merge lookup cua transactions/budgets/recurring van duoc phep doc theo localId de
  phat hien cross-owner collision truoc REPLACE. Khong doi thanh scoped lookup roi
  vo tinh coi row cua owner khac la khong ton tai. PK cac bang do khong doi.

## File thay doi trong dot v3

Duong dan Kotlin tuong doi voi `app/src/main/java/com/example/moneymatev2/`.
Worktree da co cac thay doi P0/SAFE va IDE/Firebase truoc dot nay; khong revert chung.

| Nhom | File |
| --- | --- |
| Schema | `data/local/entity/CategoryEntity.kt`, `data/local/AppDatabase.kt`, `data/local/migration/Migration2To3.kt`, `core/di/DatabaseModule.kt` |
| DAO | `data/local/dao/CategoryDao.kt`, `data/local/dao/TransactionDao.kt` |
| Relation cu | `data/local/entity/TransactionWithCategory.kt` (xoa type khong duoc dung) |
| Repositories | `data/repository/CategoryRepositoryImpl.kt`, `TransactionRepositoryImpl.kt`, `BudgetRepositoryImpl.kt`, `RecurringTransactionRepositoryImpl.kt` |
| Pull | `data/remote/sync/CloudPullService.kt` |
| Domain | `domain/model/CategoryModel.kt`, `domain/repository/CategoryRepository.kt`, `TransactionRepository.kt` |
| Use cases | `domain/usecase/category/CreateCategoryUseCase.kt`, `DeleteCategoryUseCase.kt`, `domain/usecase/transaction/CreateTransactionUseCase.kt`, `GetTransactionWithCategoryUseCase.kt` |
| History | `core/util/TransactionGrouping.kt`, `presentation/home/SharedTransactionComponents.kt`, `HistoryScreen.kt` |
| UI owner | `presentation/category/AddCategoryViewModel.kt`, `AddCategoryScreen.kt`, `CategoryManagementViewModel.kt`, `CategoryManagementScreen.kt` |
| Transaction | `presentation/transaction/AddTransactionViewModel.kt`, `AddTransactionScreen.kt`, `navigation/NavGraph.kt` |
| Budget | `presentation/budget/BudgetViewModel.kt`, `BudgetScreen.kt` |
| Recurring | `presentation/recurring/RecurringTransactionViewModel.kt`, `RecurringTransactionScreen.kt` |
| Config/resources | `app/build.gradle.kts`, `app/schemas/.../2.json`, `3.json`, `app/src/main/res/values/strings.xml`, `values-en/strings.xml` |
| Tests | `app/src/test/.../data/local/CategoryMigrationTest.kt`, `data/remote/sync/RoomSyncSafetyTest.kt`, `presentation/CategoryOwnershipTest.kt` |
| Docs | `docs/P1A_V3_MIGRATION.md`, `docs/P1A_SYNC_SAFETY.md` |

## Kiem thu

`CategoryMigrationTest` dung SQLite native cua Robolectric SDK 34 va database tren file
tam rieng cho tung test. Input v2 duoc tao bang CREATE SQL/index/setupQueries cua schema
Room v2 da dong bang, dat user_version=2, dong connection, sau do mo bang AppDatabase v3
voi migrations that. Room tu validate tat ca tables/indexes. Day khong phai test chi
tao database in-memory moi. Khong doc hay copy database nguoi dung.

- v2 co 4 row moi bang, du CREATE/UPDATE/DELETE/NONE, FAILED/PENDING/SYNCED, metadata
  null va non-null, archived category, inactive recurring, orphan reference, stableId trung.
- So sanh TAT CA cot/tat ca row cua ca sau bang truoc/sau; kiem tra integrity_check,
  categoryLocalId, metadata va mo lai ba lan.
- Sau migration insert cung localId khac UID; update/archive/fail/ack cua A khong doi B.
- v1 -> v2 -> v3 giu nguyen nam bang cu va them recurring rong.
- Chen exception sau khi copy/thay bang: open that bai, schema/user_version=2 va du lieu
  duoc rollback; mo lai voi migration dung thanh cong.
- CloudPull SERVER mock tren DB da migrate cho hai UID co cung legacy UUID: ca hai
  category ton tai, row PENDING cua A khong bi doi; pull lap khong tao trung.
- Use case history chay tren DB da migrate: archived va orphan van du 4 giao dich,
  grouping khong bo giao dich; selector active loai archived.
- RoomSyncSafetyTest giu tests SAFE va them pull A->B->A co legacy ID trung, snapshot
  category thay doi trong cung mili-giay khi upload, khong ack nham owner/version.
- CategoryOwnershipTest kiem tra budget archived/orphan, stale delete/save/category
  picker/navigation result, form chuyen tai khoan, tao category/recurring tu owner cu.

Ket qua kiem tra ngay 2026-10-15:

| Kiem tra | Ket qua |
| --- | --- |
| `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain` | BUILD SUCCESSFUL, 46 tests, 0 failures/errors/skipped |
| CategoryMigrationTest | 5 PASS, database file v1/v2 -> Room v3, native SQLite |
| RoomSyncSafetyTest | 21 PASS |
| SyncWorkerDelegateTest | 9 PASS |
| CategoryOwnershipTest | 7 PASS |
| DefaultCategoryIdsTest / ExampleUnitTest | 3 / 1 PASS |
| `assembleDebug` | PASS, `app/build/outputs/apk/debug/app-debug.apk` |
| `git diff --check` | PASS; chi co canh bao line-ending trong worktree Windows |

Cap nhat release gate ben duoi: da chay instrumentation tren AVD rieng sau
lan bao cao ban dau. Khong kiem thu Firebase production, giao dien Compose tren
thiet bi chinh, hay hai may that.
Con canh bao compiler-option Kotlin, experimental Flow va API deprecated; khong co loi
compile/test trong lan chay cuoi. Khong tao commit, deploy hay cai APK.

## Backup truoc rollout

1. Giu nguyen hai may chinh. Ghi nhan APK/signing key, Room version, thoi diem backup,
   so row theo bang/UID va pendingOperation. Backup rieng tung may; Firestore khong phai
   backup thay the du lieu PENDING chi co trong Room.
2. Tranh moi writer trong luc copy: UI, immediate/periodic Worker, recurring catch-up
   (ca khi mo man hinh), auth startup, receivers/alarms. Dung/quiesce app va xac nhan
   process khong con chay, khong tu mo lai. Cancel Worker don le khong du de dam bao.
   Tat mang chi ngan cloud; no khong dung local writes. Write SDK da dispatch co the
   da commit tren server, can doi chieu rieng.
3. Voi ban debuggable va quyen truy cap hop le, dung Android Studio Device Explorer
   hoac cong cu copy binary da kiem chung de lay bo file `moneymate.db`, `-wal`, `-shm`
   neu ton tai SAU khi tat ca writers dung. Khong copy rieng .db dang live, khong tron
   file tu hai thoi diem. Khong xoa WAL goc. Khong dung redirect binary PowerShell cu
   de xuat backup. SHA-256 tung file va manifest phai khop sau khi truyen.
4. Ban release khong cho run-as/Device Explorer thi khong uninstall de lay debug build.
   Can co duong export da kiem chung (vi du signed maintenance build van Room v2,
   khong auto sync va dong DB truoc snapshot) hoac co che backup duoc phe duyet.
   Chua co backup restore duoc thi tam dung rollout. `allowBackup=true` khong phai
   bang chung da co ban backup dung thoi diem.
5. Giu ban goc chi doc; lam viec tren ban sao trong vung han che truy cap. Mo clone
   bang SQLite de replay WAL, chay integrity_check, doi chieu version/schema, tat ca
   cot/row/PENDING/reference va hash noi dung. Co the dung SQLite backup API trong
   cong cu da kiem chung de tao snapshot nhat quan.

WAL la mot phan cua trang thai database; cac commit co the chua nam trong .db.
Xem [SQLite WAL](https://www.sqlite.org/wal.html) va
[SQLite Backup API](https://www.sqlite.org/backup.html).

## Restore va nang cap thu

1. Restore clone vao instance test cach ly, Firebase/network bi vo hieu hoa, chu dong
   ngan Worker/recurring/receiver startup. Mo clone voi build v2 truoc de chung minh
   backup restore duoc; khong chi kiem tra file ton tai. Khong tro app test vao Firebase that.
2. Nang cap instance test nay len v3, chay lai counts, metadata, integrity_check va truy
   van orphan; mo/dong lai nhieu lan. Kiem tra A->B->A, cung legacy UUID, archived,
   missing category, pending edit/delete; inactive recurring phai van inactive.
3. Test hai instance/tai khoan synthetic voi Firebase Emulator hoac project test:
   A tao offline -> online -> B nhan; B tao -> A nhan; pull/upload lap; chuyen owner
   trong sync; so sanh document ID va categoryLocalId truoc/sau. Migration khong duoc
   tao cloud document moi chi de doi khoa Room.
4. Sau khi cac buoc tren PASS, xin phe duyet rieng cho tung may chinh. Xac nhan backup
   gan nhat, du dung luong disk cho table copy, dung signing key/applicationId va
   versionCode rollout. Room v3 khong dong nghia versionCode APK=3; dot nay khong tang
   versionCode. Khong uninstall/clear storage de vuot loi cai dat.

Khi migration fail: giu database va WAL, giu backup, thu thap exception/schema/disk
trong moi truong han che, chan rollout va sua forward. Khong destructive fallback,
reset hay "thu lai" bang cach xoa DB. Test da chung minh rollback transactional;
van phai xu ly rieng hu hong file/disk/hardware ngoai pham vi test.

Sau khi migrate thanh cong, APK Room v2 khong doc duoc database v3 theo duong upgrade
binh thuong. Neu co hai owner cung ID thi downgrade PK don se mat tinh duy nhat.
Uu tien forward-fix. Restore backup v2 chi duoc xem xet khi da luu va doi chieu moi
write local/cloud sau backup, nguoi dung phe duyet va co ke hoach bao toan chung.
Khong de backup cu ghi de PENDING moi; khong tu dong downgrade migration.

## Du lieu da bi ghi de va thiet bi APK cu

Migration bao toan nhung gi hien con trong SQLite. No khong the phuc hoi noi dung
category da mat truoc do. Tim orphan bang LEFT JOIN tren ca owner va ID, vi du:

```sql
SELECT t.userId, t.localId, t.categoryLocalId
FROM transactions t
LEFT JOIN categories c
  ON c.userId = t.userId AND c.localId = t.categoryLocalId
WHERE c.localId IS NULL;
```

Lap lai cho budgets va recurring_transactions tren CLONE. Doi chieu cloud dung UID
va backup dung may/tai khoan. Khong lay row tai khoan khac de dien vao cho trong,
khong tu reassign, xoa row hay ghi de pending. Neu cloud con category goc, pull dung
UID co the tai lai sau migration; neu ca cloud va backup deu mat thi khong suy doan
duoc noi dung goc. Inactive template khong duoc tu bat lai sau khi category xuat hien.

Mixed-version: payload va Firestore paths khong doi nen giao tiep cloud van tuong thich.
APK cu van co loi PK don tren chinh may cu khi doi tai khoan legacy; v3 khong sua duoc
database cua may do. Hai ban cu/moi cung seed khi chua thay cloud co the tao default
ID khac nhau. Khong gop cac category nay trong migration; test rollout can doi chieu.

Khong can deploy Rules hay migrate Firestore cho thay doi nay. Cac rui ro P1-B/P1-C
(concurrent edits/delete resurrection, clock skew, budget/recurring concurrency) van
ngoai pham vi. Ket qua build/test khong chung minh an toan tuyet doi tren du lieu that.

## Release gate: Android instrumentation va thu nang cap APK

`CategoryMigrationAndroidTest` trong `src/androidTest` dung `MigrationTestHelper`
doc **exported Room 2.json**, tao DB v2 rieng (`gate-v2-*.db`, khong phai
`moneymate.db`), chen du lieu thu va goi `runMigrationsAndValidate(..., 3, true,
MIGRATION_2_3)`. Test so sanh tat ca cot/row cua sau bang truoc-sau, cac ref
`categoryLocalId`, archived, PENDING/FAILED/SYNCED, CREATE/UPDATE/DELETE/NONE,
remote timestamp/retry metadata, schema PRAGMA (cot, affinity, NOT NULL, default,
PK thu tu, index/unique/cot index), `integrity_check`, va mo lai bang Room v3.
Test thu hai chen cung legacy UUID cho hai UID sau migration, archive B va kiem
tra A khong doi. Test Robolectric `CategoryMigrationTest` van la SQLite native
tren JVM; no khong thay the test instrumentation.

Ket qua gate 2026-10-15: `assembleDebugAndroidTest` PASS; 2 test migration
instrumentation va `ExampleInstrumentedTest` PASS (3/3) tren AVD rieng
`MoneyMateMigrationGateD`, Android 37.2, serial `emulator-5556`. Chay bang
`adb -s emulator-5556 shell am instrument` sau khi cai debug/test APK tren
AVD sach, khong chay Gradle `connectedDebugAndroidTest` vi AVD `emulator-5554`
da co MoneyMate va Gradle connected task co the chay tren ca hai AVD. AVD
`emulator-5554` khong bi cai dat/chay test. Runtime test ban dau FAIL vi
`kotlinx-serialization-core` 1.7.3 bi chon cung JSON 1.8.1 cua room-testing;
da pin core 1.8.1 chi cho debug, test chay lai PASS. Kiem thu cai de APK v2 -> v3
duoc ghi rieng o muc cuoi tai lieu.

Chua co `1.json` duoc export tu ban APK v1, vi vay `MigrationTestHelper` khong
the xac thuc v1 -> v2 -> v3 bang schema lich su that. Test v1 Robolectric trong
repo tao schema nam bang tu v2, chi la chung cu yeu hon. Truoc khi rollout cho
nguoi dung tung dung v1, can co v1 schema/DB mau tu ban da phat hanh va test
nang cap tren clone do. Export v2 trong repo duoc suy ra tu source/commit v2;
can doi chieu identity hash/schema voi APK v2 da phat hanh va ban sao thiet bi.

Thu nang cap APK tren **AVD moi, chi voi du lieu synthetic**:

1. Dung source APK v2 da phat hanh hoac build tu commit v2 trong worktree rieng.
   Giu cung `applicationId` va debug signing key voi APK v3. Chon Firebase
   project test/emulator, khong dung tai khoan production. Cai APK v2 len AVD
   sach; tao dataset thu qua UI/test fixture rieng: archived category, ref
   transaction/budget/recurring, orphan, du bon pendingOperation, metadata co
   gia tri. Khong chay fixture truc tiep tren hai may chinh.
2. Ghi lai Room version, row count theo bang/UID, ID/ref, pending status va
   `PRAGMA integrity_check`. Sao luu clone v2 sau khi quiesce writers; mo ban
   sao tren AVD thu khac de xac nhan co the restore. `am force-stop` giup ngan
   process tren AVD thu, nhung phai xac nhan khong co writer khac va khong mo lai
   truoc khi snapshot nhat quan hoan tat. Khong copy rieng `.db` khi WAL dang ghi.
3. `adb -s <serial-AVD> install -r app/build/outputs/apk/debug/app-debug.apk`
   (khong uninstall, clear data hay `-d`). Mo v3, doi chieu lai toan bo counts,
   ID/ref, pending/archived, integrity va lich su UI; force-stop/mo lai nhieu lan.
   Theo doi log migration, khong cho sync len production. Thu lai tren clone
   v2 co WAL/pending va clone v2 da checkpoint.
4. Du lieu v2 co the cu tru tren hai thiet bi voi schema khac nhau do lich su
   build; lay backup hop le tung thiet bi va test nang cap tung clone rieng.

Build hien tai co `applicationId=com.example.moneymatev2`, `versionCode=1` ca
debug/release. Room version 3 **khong** phai versionCode APK 3. Debug APK dung
debug key cua host va co `run-as`; release khong khai bao signingConfig trong
Gradle va khong the xem nhu cung chu ky debug. Truoc phat hanh can xac nhan
signing key va tang versionCode phu hop kenh phan phoi, nhung gate nay khong
thay doi cau hinh phat hanh. Khong cai debug APK de len release APK/data that.

`allowBackup=true` va XML backup/data-extraction hien la template, khong chung
minh da co snapshot/restore dung. Voi debug AVD, dung Device Explorer/cong cu
binary sau khi writers da dung, copy nhat quan ca `.db`, `-wal`, `-shm` neu co;
hash manifest, mo clone bang SQLite/Room v2 va so sanh tat ca row, pending va
`integrity_check`. Voi release khong debuggable, `run-as` thuong khong kha dung:
can duong backup duoc ho tro/kiem chung (vi du exporter trong maintenance build
duoc ky dung key) va **restore rehearsal** truoc rollout. Khong uninstall, reset
hay gia dinh Firestore da giu cac local PENDING. Khong restore ban v2 de len
v3 da co writes moi; uu tien forward-fix va doi chieu moi thay doi sau backup.

## Ket qua cai de APK v2 -> v3 tren AVD synthetic

Ngay 2026-10-15, dung AVD moi `MoneyMateReleaseGate`, Android 37.2,
`emulator-5556`, `airplane_mode_on=1`. APK v2 debug build tu Git commit
`c936a78` trong worktree rieng `D:\MoneyMateV2GateSource`; source main cua
worktree khong thay doi. APK v2 SHA-256:
`B7A4ED4971F7C0D81A4E28354EB72EB2158AC23FA10E9200188A6358C857EFD6`.
APK v3 debug trong worktree hien tai SHA-256:
`A06DE722D853CC5AB60EDAD7AF22D1D127CC8906B8DA36C7D772F74714371643`.
Hai APK deu co applicationId `com.example.moneymatev2`, versionCode `1`,
versionName `1.0`, cert SHA-256
`FDF447DE4420DA3BC6015F64771C1738FC8A594D90F4994C18D8BA0CA689BC7E`.
Day la **APK v2 tu Git**, chua chung minh trung hash/cert/source voi APK da
cai tren hai dien thoai that; can lay thong tin ban da phat hanh de doi chieu.

Fixture test-only trong worktree v2 mo `AppDatabase` v2 that, identity hash
`580c9c39a4c22c874a7ff3cb0463a5a3`, tao 4 row moi bang: transactions,
categories, budgets, reminders, recurring_transactions, users (24 row).
Category legacy UUID `8f14e45f-0000-0000-0000-000000000001` duoc archived;
transactions/budgets/recurring cua A va B co ref den ID nay. Co CREATE,
UPDATE, DELETE, NONE va SYNCED, PENDING, FAILED; recurring/reminder inactive
de khong phat sinh job. Snapshot tat ca cot/row nam trong app test files.

`adb -s emulator-5556 install -r` APK v3: PASS, khong uninstall/clear data.
Manifest fixture con sau install; `MainActivity` launch COLD thanh cong.
`InstalledV2UpgradeAndroidTest` tren DB ung dung `moneymate.db` PASS 4/4:
Room mo version 3, identity hash
`bbfe7747867b090605eb1f52ef7311d4`; tat ca 24 row/cot/ID/ref/
pending/metadata goc bang snapshot v2; `integrity_check=ok`.
Insert category B cung legacy UUID khong doi category A; join theo UID cua
transaction, budget, recurring B deu giai duoc dung category B. DAO nguon
du lieu cho History, Budget, Categories, Reminder va Recurring doc duoc row
theo UID; archived category van giai duoc ref lich su. Force-stop, mo lai,
reboot AVD, mo lai va chay lai cac test bao toan: PASS. `/data` con khoang
4.66 GiB tren AVD sau reboot. Day la free space cua AVD, khong phai hai may that.

Backup debug synthetic: test v2 dung `VACUUM INTO` tao snapshot SQLite nhat
quan 147456 byte, mo ban clone `gate-restored-v2.db` bang Room v2 va so sanh
toan bo 24 row/cot, `integrity_check=ok`. Ban sao duoc xuat ra host
`D:\MoneyMateGateBackup-v2.db`; SHA-256 host/AVD deu la
`48B031244C1F49F0B17B74E92FEA5CD1C8AA39AE37F9C032E0451224AD71ABFD`.
Sau install v3, Room nang cap ban clone v2 nay va so sanh toan bo row: PASS.
File tu host duoc push tro lai AVD vao DB rieng `gate-host-import-v2.db`;
SHA-256 van khop, Room nang cap len v3, doi chieu 24 row va
`integrity_check=ok`: PASS. Day la round-trip file DB synthetic trong app
sandbox, chua la restore toan bo app tren thiet bi khac.
Test rieng tao clone v2, bat WAL, commit metadata moi vao category dang
`pendingOperation=CREATE`, xac nhan file `-wal` co du lieu truoc khi
`VACUUM INTO`; snapshot restore vao clone va nang cap Room v3 van co
metadata moi va pending CREATE, `integrity_check=ok`: PASS.
`VACUUM INTO` lay snapshot logic nhat quan qua SQLite, khong copy rieng file
`.db` khi writer dang chay. Chua test ban release khong debuggable, ban backup
tu hai may that, hoac restore toan bo app vao AVD/phone khac.

Sau khi them test gate, `testDebugUnitTest` 46/46 PASS, `assembleDebug` va
`assembleDebugAndroidTest` PASS, `git diff --check` exit 0. Instrumentation
khong tham so: 3 PASS, gated test SKIP; chay gate `verify-v3`: 4 PASS.

## Dieu kien phat hanh con thieu

- `signingReport`: debug dung debug.keystore tren host; release `Config:null`.
  Khong co signed release APK trong build outputs. Can doi chieu cert cua APK
  da cai tren hai may voi APK moi; khong duoc cai debug de len release data.
- versionCode v2/v3 debug deu `1`, `adb install -r` van thanh cong tren AVD.
  Phat hanh can versionCode lon hon ban da cai (neu ban that la `1`, de xuat `2`),
  va signing key phu hop. Chua sua build config trong gate nay.
- Chua co hash/APK v2 da cai tren hai dien thoai. Ket qua APK Git v2 la bang
  chung dai dien cho schema commit v2, chua chung minh dung binary thuc te.
- UI Compose History/Budget/Category/Reminder/Recurring va chuyen auth
  A -> B -> A chua xem duoc voi fixture offline: UID synthetic khong co
  Firebase test credentials. DAO/join theo UID da kiem tra, khong thay the
  test giao dien va auth lifecycle.
- Ban release khong root/run-as can co duong backup/restore da duoc thu tren
  staging dung cert va build variant. `allowBackup=true` va XML hien la template;
  khong coi Auto Backup la snapshot PENDING co the restore khi can. Neu can,
  thiet ke signed maintenance exporter dung SQLite backup API/`VACUUM INTO`,
  encrypt at rest va test restore tren clone; chua them chuc nang nay.
- Can kiem tra free space thuc te moi may, backup tung may va restore rehearsal
  truoc rollout. Neu migration fail, giu file DB/WAL va backup, thu thap loi,
  sua forward; khong clear data hoac downgrade truc tiep Room v3.

Quy trinh thu tren hai may that khi cac dieu kien tren duoc dap ung: doi chieu
APK/cert/versionCode; tam dung writers va lay backup rieng A va B; restore
tung backup vao clone va test upgrade; nang cap A truoc, kiem tra PENDING va
Room/UI/sync; chi sau khi A dat moi nang cap B va doi chieu hai chieu. Khong
can deploy Firebase Rules/Firestore cho thay doi PK Room nay. Ket luan gate
hien tai: **NOT READY** cho hai may chinh vi exact deployed APK, release
signing va release backup/restore chua duoc chung minh.
