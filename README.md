# CoffeeBeanApp

## Deskripsi

**CoffeeBeanApp** adalah aplikasi mobile yang terintegrasi dengan sistem Deep Learning untuk **mengidentifikasi kualitas biji kopi** secara otomatis. Sistem ini dirancang untuk mempermudah pelaku industri kopi, seperti petani dan pengusaha, dalam menilai mutu biji kopi menggunakan teknologi digital.

---
## Teknologi & Model

Proyek ini menggunakan berbagai arsitektur *Convolutional Neural Network (CNN)* untuk mengklasifikasikan kualitas biji kopi:

- ✅ DenseNet121  
- ✅ EfficientNetB0  
- ✅ MobileNetV2  
- ✅ ResNet50  
- ✅ Xception

📊 Evaluasi model dilakukan menggunakan metrik:
- Akurasi
- Confusion Matrix
- Precision, Recall, dan F1-Score

---
## Integrasi ke Aplikasi Mobile

Model yang telah dilatih akan diintegrasikan ke aplikasi mobile menggunakan TensorFlow Lite.

### Fitur Utama:
- Mengunggah gambar biji kopi
- Melakukan scan langsung pada biji kopi
- Hasil klasifikasi kualitas serta presentase (contoh: Defect atau Good Quality)
