package com.suraksha.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FieldValue
import com.suraksha.app.databinding.ActivityMainBinding
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var contactsManager: ContactsManager

    private var holdHandler = Handler(Looper.getMainLooper())
    private var holdRunnable: Runnable? = null
    private val HOLD_DURATION = 3000L
    private var sosActive = false
    private var currentLat = 0.0
    private var currentLng = 0.0

    private var lastShakeTime = 0L
    private var shakeCount = 0
    private val SHAKE_THRESHOLD = 15f
    private val SHAKE_RESET_MS = 2000L
    private val SHAKES_REQUIRED = 3

    private val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.RECORD_AUDIO
    )
    private val PERMISSION_REQUEST = 100

    // Power button receiver
    private var powerButtonReceiver: PowerButtonReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contactsManager = ContactsManager(this)
        window.statusBarColor = android.graphics.Color.parseColor("#080808")
        FirebaseAuth.getInstance().signInAnonymously()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        requestPermissions()
        setupSosButton()
        setupNavigation()
        setupContactsScreen()
        setupQuickActions()
        registerPowerButtonReceiver()
        showScreen("home")
        getLocation()

        // Handle power button trigger from intent
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    // ── Power button intent handler ────────────────────────────────────────
    private fun handleIncomingIntent(intent: Intent) {
        if (intent.getBooleanExtra("TRIGGER_SOS", false)) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!sosActive) triggerSOS()
            }, 500)
        }
    }

    // ── Register power button receiver ─────────────────────────────────────
    private fun registerPowerButtonReceiver() {
        powerButtonReceiver = PowerButtonReceiver()
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(powerButtonReceiver, filter)
    }

    // ── Get Location ───────────────────────────────────────────────────────
    private fun getLocation() {
        LocationService.getLastLocation(this) { lat, lng ->
            currentLat = lat
            currentLng = lng
            runOnUiThread {
                binding.statusText.text = "Location active — you are safe"
            }
        }
    }

    // ── Contacts Screen ────────────────────────────────────────────────────
    private fun setupContactsScreen() {
        refreshContactsList()
        binding.addContactBtn.setOnClickListener {
            val name = binding.contactNameInput.text.toString().trim()
            val phone = binding.contactPhoneInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phone.isEmpty() || phone.length < 10) {
                Toast.makeText(this, "Enter full number with country code e.g. 919876543210",
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            contactsManager.saveContact(name, phone)
            binding.contactNameInput.setText("")
            binding.contactPhoneInput.setText("")
            refreshContactsList()
            Toast.makeText(this, "$name added!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshContactsList() {
        contactsManager.loadContactsIntoView(binding.contactsList)
    }

    // ── Quick Actions ──────────────────────────────────────────────────────
    private fun setupQuickActions() {
        binding.btnShareLocation.setOnClickListener {
            if (currentLat == 0.0) {
                Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show()
                getLocation(); return@setOnClickListener
            }
            val contacts = contactsManager.getContacts()
            if (contacts.isEmpty()) {
                Toast.makeText(this, "Add trusted contacts first!", Toast.LENGTH_SHORT).show()
                showScreen("contacts"); return@setOnClickListener
            }
            val mapsLink = "https://maps.google.com/?q=$currentLat,$currentLng"
            sendSMSToAll("Hi, sharing my live location.\n\nLocation: $mapsLink\n\nSent via Suraksha")
            Toast.makeText(this, "Location sent to ${contacts.size} contacts!", Toast.LENGTH_LONG).show()
        }
        binding.btnFakeCall.setOnClickListener { showFakeCallScreen() }
    }

    private fun showFakeCallScreen() {
        val contacts = contactsManager.getContacts()
        val callerName = if (contacts.isNotEmpty()) contacts[0].name else "Amma"
        AlertDialog.Builder(this)
            .setTitle("Fake incoming call")
            .setMessage("Phone will vibrate as if $callerName is calling.")
            .setPositiveButton("Start") { _, _ ->
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000), -1))
                }
                AlertDialog.Builder(this)
                    .setTitle("Incoming call — $callerName")
                    .setMessage("Mobile")
                    .setPositiveButton("Answer") { d, _ ->
                        (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel(); d.dismiss() }
                    .setNegativeButton("Decline") { d, _ ->
                        (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel(); d.dismiss() }
                    .setCancelable(false).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ── SOS Button ─────────────────────────────────────────────────────────
    private fun setupSosButton() {
        binding.sosButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startHold()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelHold()
            }
            true
        }
        binding.cancelSosBtn.setOnClickListener { cancelSOS() }
    }

    private fun startHold() {
        if (sosActive) return
        binding.sosLabel.text = "HOLD..."
        binding.sosButton.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = HOLD_DURATION
            addUpdateListener {
                binding.sosProgressRing.progress = ((it.animatedValue as Float) * 100).toInt()
            }
        }
        animator.start()
        holdRunnable = Runnable { triggerSOS() }
        holdHandler.postDelayed(holdRunnable!!, HOLD_DURATION)
    }

    private fun cancelHold() {
        if (sosActive) return
        holdHandler.removeCallbacks(holdRunnable ?: return)
        binding.sosLabel.text = "SOS"
        binding.sosButton.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        binding.sosProgressRing.progress = 0
    }

    // ── Volume Button ──────────────────────────────────────────────────────
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!sosActive) { triggerSOS(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ── Shake Detection ────────────────────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        if (acceleration > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > SHAKE_RESET_MS) shakeCount = 0
            shakeCount++
            lastShakeTime = now
            if (shakeCount >= SHAKES_REQUIRED && !sosActive) {
                shakeCount = 0; triggerSOS()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Trigger SOS ────────────────────────────────────────────────────────
    fun triggerSOS() {
        sosActive = true
        binding.sosButton.setBackgroundColor(android.graphics.Color.parseColor("#dc2626"))
        binding.sosLabel.text = "ACTIVE"

        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 500, 200, 500, 200, 500), -1))
        }

        val contacts = contactsManager.getContacts()
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No contacts added! Go to Contacts tab first.",
                Toast.LENGTH_LONG).show()
            sosActive = false
            binding.sosButton.setBackgroundColor(android.graphics.Color.parseColor("#ff3333"))
            binding.sosLabel.text = "SOS"
            return
        }

        Toast.makeText(this, "SOS Activated! Alerting ${contacts.size} contacts...",
            Toast.LENGTH_LONG).show()

        // Start background location tracking
        ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java))

        // Start audio recording for evidence
        AudioRecordingService.start(this)

        // Get location then send all alerts
        LocationService.getLastLocation(this) { lat, lng ->
            currentLat = lat
            currentLng = lng
            val mapsLink = "https://maps.google.com/?q=$lat,$lng"

            // SMS to trusted contacts only — NO unknown volunteers
            val message = "EMERGENCY! I need help.\n\nLive location:\n$mapsLink\n\nSent via Suraksha"
            sendSMSToAll(message)

            // Save to Firestore — no FCM to unknown users
            saveAlertToFirestore(lat, lng, mapsLink)

            // Auto call first contact
            autoCallFirstContact()

            runOnUiThread {
                showScreen("alert")
                binding.mapLink.text = "Tap to open your location"
                binding.mapLink.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsLink)))
                }
                contactsManager.loadContactsIntoView(binding.alertContactsList)
            }
        }
    }

    // ── Auto Call ─────────────────────────────────────────────────────────
    private fun autoCallFirstContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return
        val contacts = contactsManager.getContacts()
        if (contacts.isEmpty()) return
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${contacts[0].phone}")))
        }, 2000)
    }

    // ── Send SMS ───────────────────────────────────────────────────────────
    private fun sendSMSToAll(message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) return
        val contacts = contactsManager.getContacts()
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        contacts.forEach { contact ->
            try {
                smsManager.sendMultipartTextMessage(
                    contact.phone, null, smsManager.divideMessage(message), null, null)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Save to Firestore (contacts only, no volunteer sharing) ───────────
    private fun saveAlertToFirestore(lat: Double, lng: Double, mapsLink: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        // NOTE: We do NOT write to a public collection or send FCM to unknown users
        // Location is only shared with trusted contacts via direct SMS above
        FirebaseFirestore.getInstance().collection("my_alerts").document(uid).set(
            hashMapOf(
                "uid" to uid,
                "lat" to lat,
                "lng" to lng,
                "location" to GeoPoint(lat, lng),
                "mapsLink" to mapsLink,
                "active" to true,
                "timestamp" to FieldValue.serverTimestamp()
            )
        )
    }

    // ── Cancel SOS ─────────────────────────────────────────────────────────
    private fun cancelSOS() {
        AlertDialog.Builder(this)
            .setTitle("Cancel SOS?")
            .setMessage("Are you safe? This will stop all alerts and recording.")
            .setPositiveButton("Yes, I am safe") { _, _ ->
                sosActive = false
                binding.sosButton.setBackgroundColor(android.graphics.Color.parseColor("#ff3333"))
                binding.sosLabel.text = "SOS"
                binding.sosProgressRing.progress = 0
                stopService(Intent(this, LocationService::class.java))
                AudioRecordingService.stop(this)  // Stop and upload recording
                showScreen("home")
                Toast.makeText(this, "SOS cancelled. Audio evidence saved.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("No, keep SOS active", null).show()
    }

    // ── Navigation ─────────────────────────────────────────────────────────
    private fun setupNavigation() {
        binding.navHome.setOnClickListener { showScreen("home") }
        binding.navAlert.setOnClickListener { showScreen("alert") }
        binding.navContacts.setOnClickListener {
            showScreen("contacts"); refreshContactsList() }
        binding.navHelper.setOnClickListener {
            showScreen("helper"); setupHelperToggle() }
    }

    private fun setupHelperToggle() {
        val prefs = getSharedPreferences("suraksha_prefs", MODE_PRIVATE)
        val isOn = prefs.getBoolean("helper_mode", false)
        binding.helperToggle.isChecked = isOn
        updateHelperToggleUI(isOn)
        binding.helperToggle.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("helper_mode", checked).apply()
            updateHelperToggleUI(checked)
            Toast.makeText(this,
                if (checked) "Helper mode ON" else "Helper mode OFF",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateHelperToggleUI(isOn: Boolean) {
        if (isOn) {
            binding.helperToggleCard.setBackgroundColor(android.graphics.Color.parseColor("#0f1f0f"))
            binding.helperToggleTitle.setTextColor(android.graphics.Color.parseColor("#4ade80"))
            binding.helperToggleStatus.setTextColor(android.graphics.Color.parseColor("#22c55e"))
            binding.helperToggleStatus.text = "You are part of the helper network"
            binding.helperToggle.thumbTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#4ade80"))
            binding.helperToggle.trackTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#1a3a1a"))
            binding.helperActiveStatus.visibility = View.VISIBLE
        } else {
            binding.helperToggleCard.setBackgroundColor(android.graphics.Color.parseColor("#0f0f0f"))
            binding.helperToggleTitle.setTextColor(android.graphics.Color.parseColor("#888888"))
            binding.helperToggleStatus.setTextColor(android.graphics.Color.parseColor("#444444"))
            binding.helperToggleStatus.text = "Toggle on to join the helper network"
            binding.helperToggle.thumbTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#666666"))
            binding.helperToggle.trackTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#2a2a2a"))
            binding.helperActiveStatus.visibility = View.GONE
        }
    }

    fun showScreen(name: String) {
        binding.screenHome.visibility = if (name == "home") View.VISIBLE else View.GONE
        binding.screenAlert.visibility = if (name == "alert") View.VISIBLE else View.GONE
        binding.screenContacts.visibility = if (name == "contacts") View.VISIBLE else View.GONE
        binding.screenHelper.visibility = if (name == "helper") View.VISIBLE else View.GONE
    }

    // ── XML onClick handlers ───────────────────────────────────────────────
    fun callPolice(view: View) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:100")))
    }

    fun callAmbulance(view: View) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:108")))
    }

    fun sendTestSMS(view: View) {
        val contacts = contactsManager.getContacts()
        if (contacts.isEmpty()) {
            Toast.makeText(this, "Add contacts first!", Toast.LENGTH_SHORT).show(); return
        }
        if (currentLat == 0.0) getLocation()
        val link = if (currentLat != 0.0) "https://maps.google.com/?q=$currentLat,$currentLng"
        else "https://maps.google.com"
        sendSMSToAll("TEST from Suraksha App — ignore this.\n\nTest location: $link")
        Toast.makeText(this, "Test SMS sent to ${contacts.size} contacts!", Toast.LENGTH_LONG).show()
    }

    // Opens Google Maps with nearby safe places pre-searched
    fun findNearbyPlaces(view: View) {
        if (currentLat == 0.0) {
            Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show()
            getLocation(); return
        }
        // Opens Google Maps searching hospitals + police near current location
        val uri = Uri.parse("geo:$currentLat,$currentLng?q=hospital+police+pharmacy+near+me")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback if Maps not installed
            val webUri = Uri.parse("https://maps.google.com/maps?near=$currentLat,$currentLng&q=hospital")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    // ── Permissions ────────────────────────────────────────────────────────
    private fun requestPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), PERMISSION_REQUEST + 1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST + 2)
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(powerButtonReceiver) } catch (e: Exception) {}
    }
}