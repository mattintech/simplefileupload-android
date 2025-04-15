
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
- [x] Add `<intent-filter>` in `AndroidManifest.xml` for ACTION_SEND and ACTION_SEND_MULTIPLE
- [x] Handle incoming shared files in `MainActivity` or a new controller
- [x] Automatically start the upload process from shared intent

### 📦 Zip Before Upload
- [x] Detect if selected/shared file is already a `.zip`
- [x] If not zipped, compress it to `.zip` format
- [x] Use the zipped version for upload

### 📜 Upload History
- [x] Create a local database or use SharedPreferences to log:
    - File name
    - Upload time
    - Server-returned code
    - Max downloads & expiry
- [x] Add new UI to list previously uploaded files
- [x] Allow copy of upload code and option to delete history entry

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
- [x] Manual upload with various file types
- [x] Share intent upload flow
- [x] Zip logic works on large files
- [x] Network disruptions (offline/retry handling)
- [x] UI responsiveness on phones and tablets

---

## 🧼 Code Quality
- [x] Ensure MVC separation in all components
- [ ] Add unit tests for:
    - Zip utility
    - Upload manager
    - History storage
    - Share intent handler
- [x] Improve logs for debugging failures
- [x] Ensure compatibility with Android 14+ (API 34)

---

## 🚀 Future Ideas
- [ ] Dark mode UI
- [ ] Multi-file upload
- [ ] Upload status notification with retry option
- [ ] Settings UI to customize upload URL
- [ ] Delete from client deletes from server 
- [ ] Improve Icons and overall UI to be less ugly
- [ ] Add iOS support.  (Only if someone asks...)
- [ ] Let user define max downloads and save the setting in a shared preference
- [ ] Multi-Dumpstate upload 
- [ ] Dumpstate cleanup after successful upload
- [ ] Provide steps of how to collect a dumpstate and download the server
