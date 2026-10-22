package vn.edu.vhu.ltdd.a7permission;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "A7_231A290134";

    private static final String CHANNEL_ID = "a7_channel";
    private static final int NOTI_ID = 1001;

    private ImageView imgAnh;
    private TextView tvTrangThai, tvLienHe;

    // ---------------------------------------------------------------
    // Mọi launcher khai báo ở CẤP FIELD: phải đăng ký trước khi Activity STARTED.
    // ---------------------------------------------------------------

    // 1) Xin quyền CAMERA
    private final ActivityResultLauncher<String> xinQuyenCamera = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                Log.d(TAG, "Kết quả xin quyền CAMERA: " + duocCap);
                if (duocCap) {
                    moCamera();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    hienGiaiThich();                       // từ chối nhưng còn hỏi lại được
                } else {
                    hienMoCaiDat(R.string.blocked_camera); // bị chặn vĩnh viễn → dẫn vào Cài đặt
                }
            });

    // 2) Xin quyền POST_NOTIFICATIONS (chỉ cần từ Android 13 / API 33)
    private final ActivityResultLauncher<String> xinQuyenThongBao = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                if (duocCap) {
                    guiThongBao();
                } else {
                    Toast.makeText(this, R.string.noti_denied, Toast.LENGTH_SHORT).show();
                }
            });

    // 3) Mở Camera lấy ảnh xem trước
    private final ActivityResultLauncher<Void> chupAnh = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            (Bitmap bitmap) -> {
                if (bitmap != null) {
                    imgAnh.setImageBitmap(bitmap);
                    tvTrangThai.setText(getString(R.string.status_photo,
                            bitmap.getWidth(), bitmap.getHeight()));
                } else {
                    Toast.makeText(this, R.string.photo_cancelled, Toast.LENGTH_SHORT).show();
                }
            });

    // NC1) Xin HAI quyền cùng lúc – callback nhận Map<tên quyền, được cấp hay không>
    private final ActivityResultLauncher<String[]> xinHaiQuyen = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            (Map<String, Boolean> ketQua) -> {
                for (Map.Entry<String, Boolean> e : ketQua.entrySet()) {
                    Log.d(TAG, "NC1 – " + e.getKey() + " = " + e.getValue());
                }
                // Xử lý TỪNG quyền riêng
                Boolean camera = ketQua.get(Manifest.permission.CAMERA);
                Boolean thongBao = ketQua.get(Manifest.permission.POST_NOTIFICATIONS); // null nếu < API 33

                Toast.makeText(this, getString(R.string.nc1_ket_qua,
                        tenKetQua(camera), tenKetQua(thongBao)), Toast.LENGTH_LONG).show();

                if (Boolean.FALSE.equals(camera)) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        hienGiaiThich();
                    } else {
                        hienMoCaiDat(R.string.blocked_camera);
                    }
                }
                if (Boolean.TRUE.equals(thongBao)) {
                    guiThongBao();
                }
            });

    // NC2) Xin quyền READ_CONTACTS
    private final ActivityResultLauncher<String> xinQuyenDanhBa = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                Log.d(TAG, "Kết quả xin quyền READ_CONTACTS: " + duocCap);
                if (duocCap) {
                    docDanhBa();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                    hienGiaiThichDanhBa();
                } else {
                    hienMoCaiDat(R.string.blocked_contacts);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        imgAnh = findViewById(R.id.imgAnh);
        tvTrangThai = findViewById(R.id.tvTrangThai);
        tvLienHe = findViewById(R.id.tvLienHe);
        Button btnChupAnh = findViewById(R.id.btnChupAnh);
        Button btnThongBao = findViewById(R.id.btnThongBao);
        Button btnXinCaHai = findViewById(R.id.btnXinCaHai);
        Button btnDocDanhBa = findViewById(R.id.btnDocDanhBa);
        Button btnCaiDat = findViewById(R.id.btnCaiDat);

        taoKenhThongBao();

        btnChupAnh.setOnClickListener(v -> kiemTraRoiChup());
        btnThongBao.setOnClickListener(v -> kiemTraRoiGuiThongBao());
        btnXinCaHai.setOnClickListener(v -> xinCaHaiQuyen());
        btnDocDanhBa.setOnClickListener(v -> kiemTraRoiDocDanhBa());
        btnCaiDat.setOnClickListener(v -> moCaiDatUngDung());
    }

    @Override
    protected void onResume() {
        super.onResume();
        capNhatTrangThai();   // người dùng có thể vừa đổi quyền trong Cài đặt rồi quay lại
    }

    // =====================  CAMERA: MẪU 3 NHÁNH  =====================

    private void kiemTraRoiChup() {
        int trangThai = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        if (trangThai == PackageManager.PERMISSION_GRANTED) {
            moCamera();                                                  // Nhánh 1: đã có quyền
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            hienGiaiThich();                                             // Nhánh 2: giải thích rồi hỏi lại
        } else {
            xinQuyenCamera.launch(Manifest.permission.CAMERA);           // Nhánh 3: lần đầu / bị chặn
        }
    }

    private void hienGiaiThich() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.rationale_title)
                .setMessage(R.string.rationale_camera)
                .setPositiveButton(R.string.agree,
                        (d, w) -> xinQuyenCamera.launch(Manifest.permission.CAMERA))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Hộp thoại dẫn vào Cài đặt; nội dung thay đổi theo quyền đang bị chặn. */
    private void hienMoCaiDat(int noiDung) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.blocked_title)
                .setMessage(noiDung)
                .setPositiveButton(R.string.open_settings, (d, w) -> moCaiDatUngDung())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void moCamera() {
        chupAnh.launch(null);
    }

    /** Mở đúng trang thông tin ứng dụng của app này trong Cài đặt. */
    private void moCaiDatUngDung() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(i);
    }

    // =====================  NC1: XIN HAI QUYỀN CÙNG LÚC  =====================

    private void xinCaHaiQuyen() {
        // POST_NOTIFICATIONS chỉ tồn tại từ API 33 nên phải kiểm tra phiên bản trước
        String[] danhSach = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? new String[]{Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS}
                : new String[]{Manifest.permission.CAMERA};
        xinHaiQuyen.launch(danhSach);
    }

    private String tenKetQua(Boolean giaTri) {
        if (giaTri == null) {
            return getString(R.string.not_applicable);
        }
        return giaTri ? getString(R.string.granted) : getString(R.string.denied);
    }

    // =====================  NC2: ĐỌC DANH BẠ  =====================

    private void kiemTraRoiDocDanhBa() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            docDanhBa();                                                       // Nhánh 1
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            hienGiaiThichDanhBa();                                             // Nhánh 2
        } else {
            xinQuyenDanhBa.launch(Manifest.permission.READ_CONTACTS);          // Nhánh 3
        }
    }

    private void hienGiaiThichDanhBa() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.rationale_title)
                .setMessage(R.string.rationale_contacts)
                .setPositiveButton(R.string.agree,
                        (d, w) -> xinQuyenDanhBa.launch(Manifest.permission.READ_CONTACTS))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Truy vấn ContentResolver và hiển thị tối đa 5 liên hệ đầu tiên. */
    private void docDanhBa() {
        StringBuilder sb = new StringBuilder();
        String cotTen = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY;
        try (Cursor c = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{cotTen}, null, null, cotTen + " ASC")) {
            if (c != null) {
                int viTriCot = c.getColumnIndexOrThrow(cotTen);
                int dem = 0;
                while (dem < 5 && c.moveToNext()) {
                    dem++;
                    sb.append(dem).append(". ").append(c.getString(viTriCot)).append('\n');
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Thiếu quyền READ_CONTACTS", e);
            Toast.makeText(this, R.string.contacts_denied, Toast.LENGTH_SHORT).show();
            return;
        }

        if (sb.length() == 0) {
            tvLienHe.setText(R.string.contacts_empty);
        } else {
            tvLienHe.setText(getString(R.string.contacts_title, sb.toString().trim()));
        }
        Log.d(TAG, "Đã đọc danh bạ:\n" + sb);
    }

    // =====================  THÔNG BÁO  =====================

    private void kiemTraRoiGuiThongBao() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            guiThongBao();   // trước Android 13 không có quyền POST_NOTIFICATIONS
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            guiThongBao();
        } else {
            xinQuyenThongBao.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Từ Android 8 (API 26) mọi thông báo đều phải thuộc về một kênh. */
    private void taoKenhThongBao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel kenh = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            kenh.setDescription(getString(R.string.channel_desc));
            getSystemService(NotificationManager.class).createNotificationChannel(kenh);
        }
    }

    private void guiThongBao() {
        // NC3: bấm vào thông báo thì mở lại app (đưa Activity đang chạy lên trước, không tạo bản mới)
        Intent moApp = new Intent(this, MainActivity.class);
        moApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, moApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.noti_title))
                .setContentText(getString(R.string.noti_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)     // NC3
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(this).notify(NOTI_ID, b.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Thiếu quyền gửi thông báo", e);
        }
    }

    // =====================  HIỂN THỊ TRẠNG THÁI  =====================

    private void capNhatTrangThai() {
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean thongBao = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
        boolean danhBa = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
        String s = getString(R.string.status_format,
                camera ? getString(R.string.granted) : getString(R.string.denied),
                thongBao ? getString(R.string.granted) : getString(R.string.denied),
                danhBa ? getString(R.string.granted) : getString(R.string.denied));
        tvTrangThai.setText(s);
        Log.d(TAG, s.replace("\n", " | "));
    }
}
