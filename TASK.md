
# 📱 Task List for SimpleLogUpload (Android App)

## 🧭 Project Goals
- Follow **MVC** architectural pattern
- Allow users to upload files manually or via **Android share intent**
- Automatically **zip uncompressed files** before upload
- Show and manage a **local history** of uploaded files
- Display the **upload code** from server after successful upload
- Support configuration for:
    - Max download count
    - Expiry time (max 24 hours)

---

## 🛠️ Core Features to Implement

### 🔗 Share Integration
- [ ] Add `<intent-filter>` in `AndroidManifest.xml` for ACTION_SEND and ACTION_SEND_MULTIPLE
- [ ] Handle incoming shared files in `MainActivity` or a new controller
- [ ] Automatically start the upload process from shared intent

### 📦 Zip Before Upload
- [ ] Detect if selected/shared file is already a `.zip`
- [ ] If not zipped, compress it to `.zip` format
- [ ] Use the zipped version for upload

### 📜 Upload History
- [ ] Create a local database or use SharedPreferences to log:
    - File name
    - Upload time
    - Server-returned code
    - Max downloads & expiry
- [ ] Add new UI to list previously uploaded files
- [ ] Allow copy of upload code and option to delete history entry

### ⬆️ Upload Enhancements
- [x] Replace TinyURL logic with server-provided 6-digit alphanumeric code
- [x] Display this code to the user after upload
- [x] Validate file size before upload (max 2GB, increased from 16MB)
- [x] Add retry logic and better error messages

---

## ✅ Existing Features (Audit)
- [x] File selection and upload via UI
- [x] Background upload using JobService
- [x] Upload progress shown to user
- [x] Uses SharedPreferences for config
- [x] HTTP cleartext traffic support for server communication
- [x] Support for large file uploads (up to 2GB)

---

## 🧪 Testing Scenarios
- [ ] Manual upload with various file types
- [ ] Share intent upload flow
- [ ] Zip logic works on large files
- [x] Network disruptions (offline/retry handling)
- [ ] UI responsiveness on phones and tablets

---

## 🧼 Code Quality
- [x] Ensure MVC separation in all components
- [ ] Add unit tests for:
    - Zip utility
    - Upload manager
    - History storage
    - Share intent handler
- [x] Improve logs for debugging failures

---

## 🚀 Future Ideas
- [ ] Dark mode UI
- [ ] Multi-file upload
- [ ] Upload status notification with retry option
