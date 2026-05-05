package com.alaimtiaz.calendaralarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    companion object {
        const val PREFS_NAME    = "CalendarAlarmPrefs"
        const val KEY_SOUND_URI = "sound_uri"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: EventAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var showingPast = false

    private val ringtonePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            prefs.edit().putString(KEY_SOUND_URI, uri?.toString()).apply()
            updateSoundLabel()
            // تنبيه: تغيير النغمة يتطلب إعادة إنشاء القناة (هي immutable بعد الإنشاء)
            recreateAlarmChannel()
        }
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { updateStatuses() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        // ملاحظة: القناة تنشأ الآن في CalendarAlarmApplication.onCreate
        // لا نُنشئها هنا (كانت تنشأ بإعدادات ناقصة وتمنع تحديث القناة الصحيحة)
        setupList()
        setupSearch()
        setupTabs()
        setupPermissions()
        setupSound()
        setupTest()
        // شغّل Service مراقبة التقويم
        try { CalendarObserverService.start(this) } catch (_: Exception) {
            scope.launch { withContext(Dispatchers.IO) { AlarmScheduler.rescheduleAllUpcoming(this@MainActivity) } }
        }
    }

    override fun onResume() { super.onResume(); updateStatuses() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    /**
     * إعادة إنشاء قناة المنبه عند تغيير النغمة.
     * (NotificationChannel.setSound لا يقبل التحديث بعد الإنشاء —
     *  يجب الحذف ثم الإنشاء — والـ Application class يتولى ذلك)
     */
    private fun recreateAlarmChannel() {
        (application as? CalendarAlarmApplication)?.recreateAlarmChannel()
    }

    private fun setupList() {
        val rv = findViewById<RecyclerView>(R.id.rvEvents)
        adapter = EventAdapter(this, emptyList()) { openEvent(it) }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        loadEvents()
    }

    private fun loadEvents() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        val q = findViewById<EditText>(R.id.etSearch).text.toString().trim()
        scope.launch {
            val events = withContext(Dispatchers.IO) {
                when {
                    q.isNotBlank() -> CalendarRepository.searchEvents(this@MainActivity, q)
                    showingPast    -> CalendarRepository.getPastEvents(this@MainActivity)
                    else           -> CalendarRepository.getUpcomingEvents(this@MainActivity)
                }
            }
            adapter.updateEvents(events)
            findViewById<TextView>(R.id.tvEmpty).visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupSearch() {
        val et = findViewById<EditText>(R.id.etSearch)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { loadEvents() }
            override fun afterTextChanged(s: Editable?) {}
        })
        findViewById<ImageButton>(R.id.btnClearSearch).setOnClickListener { et.setText("") }
    }

    private fun setupTabs() {
        val btnUp   = findViewById<Button>(R.id.btnTabUpcoming)
        val btnPast = findViewById<Button>(R.id.btnTabPast)
        btnUp.setOnClickListener   { showingPast = false; loadEvents() }
        btnPast.setOnClickListener { showingPast = true;  loadEvents() }
    }

    private fun setupPermissions() {
        findViewById<Button>(R.id.btnGrantCalendar).setOnClickListener {
            permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
        }
        findViewById<Button>(R.id.btnGrantNotif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        findViewById<Button>(R.id.btnGrantExactAlarm).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = Uri.parse("package:$packageName") })
        }
        findViewById<Button>(R.id.btnGrantFullScreen).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply { data = Uri.parse("package:$packageName") })
        }
        findViewById<Button>(R.id.btnGrantNotifListener).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btnGrantBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") })
        }
        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = Uri.parse("package:$packageName") })
        }
    }

    private fun updateStatuses() {
        val calOk      = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val notifOk    = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
        val exactOk    = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(AlarmManager::class.java).canScheduleExactAlarms() else true
        val fullOk     = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) getSystemService(NotificationManager::class.java).canUseFullScreenIntent() else true
        val listenerOk = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        setStatus(R.id.tvStatusCalendar,  calOk,      "✓ التقويم: مفعّل",           "✗ التقويم: غير مفعّل")
        setStatus(R.id.tvStatusNotif,     notifOk,    "✓ الإشعارات: مفعّلة",        "✗ الإشعارات: غير مفعّلة")
        setStatus(R.id.tvStatusExact,     exactOk,    "✓ الجدولة الدقيقة: مفعّلة",  "✗ الجدولة الدقيقة: غير مفعّلة")
        setStatus(R.id.tvStatusFull,      fullOk,     "✓ شاشة كاملة: مفعّلة",       "✗ شاشة كاملة: غير مفعّلة")
        setStatus(R.id.tvStatusListener,  listenerOk, "✓ قراءة الإشعارات: مفعّلة",  "✗ قراءة الإشعارات: غير مفعّلة")
        if (calOk) loadEvents()
    }

    private fun setStatus(id: Int, ok: Boolean, okText: String, failText: String) {
        val tv = findViewById<TextView>(id)
        tv.text = if (ok) okText else failText
        tv.setTextColor(getColor(if (ok) R.color.green_ok else R.color.red_warn))
    }

    private fun setupSound() {
        updateSoundLabel()
        findViewById<Button>(R.id.btnPickSound).setOnClickListener {
            val existing = prefs.getString(KEY_SOUND_URI, null)?.let { Uri.parse(it) }
            ringtonePicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,         RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,  false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            })
        }
    }

    private fun updateSoundLabel() {
        val uriStr = prefs.getString(KEY_SOUND_URI, null)
        val name   = if (uriStr != null) try { RingtoneManager.getRingtone(this, Uri.parse(uriStr))?.getTitle(this) ?: "نغمة مخصصة" } catch (_: Exception) { "نغمة مخصصة" } else "نغمة المنبه الافتراضية"
        findViewById<TextView>(R.id.tvSoundName).text = "النغمة: $name"
    }

    private fun setupTest() {
        val testBase = { isTask: Boolean ->
            startActivity(Intent(this, AlarmOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmOverlayActivity.EXTRA_EVENT_ID,    if (isTask) 99998L else 99999L)
                putExtra(AlarmOverlayActivity.EXTRA_TITLE,       if (isTask) "مهمة تجريبية ✅" else "اختبار التنبيه 🔔")
                putExtra(AlarmOverlayActivity.EXTRA_DESCRIPTION, if (isTask) "هذا تنبيه مهمة" else "هذا تنبيه تجريبي")
                putExtra(AlarmOverlayActivity.EXTRA_LOCATION,    "")
                putExtra(AlarmOverlayActivity.EXTRA_IS_TASK,     isTask)
                putExtra(AlarmOverlayActivity.EXTRA_START_TIME,  System.currentTimeMillis())
            })
        }
        findViewById<Button>(R.id.btnTest).setOnClickListener     { testBase(false) }
        findViewById<Button>(R.id.btnTestTask).setOnClickListener { testBase(true)  }
    }

    private fun openEvent(event: CalendarEvent) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.withAppendedPath(android.provider.CalendarContract.Events.CONTENT_URI, event.id.toString())
            })
        } catch (_: Exception) {}
    }
}
