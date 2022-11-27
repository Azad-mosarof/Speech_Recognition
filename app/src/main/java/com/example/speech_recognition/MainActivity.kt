package com.example.speech_recognition

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.speech_recognition.databinding.ActivityMainBinding
import com.example.speech_recognition.object_detection.Preview
import com.example.speech_recognition.util.ContactModel
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest
import com.mvp.handyopinion.URIPathHelper
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tts: TextToSpeech
    private var RQ_CODE = 102

    private val camMan by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager}
    private lateinit var cameraId: String
    private var torchMode: Boolean = false

    private var dir: String? = null
    private var file: String? = null
    private var mediaRecorder: MediaRecorder? = null
    private var state: Boolean = false
    private var recordingStopped: Boolean = false

    private lateinit var player: MediaPlayer
    private var makeCall:Boolean = false
    private var getContact: Boolean = false
    private var getName: Boolean = false
    private var numbervalidity:Boolean = false
    private var phoneNumber: String? = null

    private lateinit var locationRequest: LocationRequest
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        dir = externalCacheDir?.absolutePath
        mediaRecorder = MediaRecorder()

        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

        if(ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO),102)
            SPEAK("Please give the Microphone Access")
        }
        else{
            SPEAK("Please tap on the microphone to say something")
        }

        binding.imageView3.setOnClickListener{
            askSpeechInput()
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == RQ_CODE && resultCode == RESULT_OK) {
            val result: ArrayList<String>? =
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            binding.textView.text = result?.get(0).toString()

            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("open","camera"))) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        111
                    )
                    SPEAK("Please give the Camera Access")
                }
                else {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    startActivityForResult(intent, 101)
                    SPEAK("Camera is successfully started")
                }
            }

            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("record","video"))) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        11
                    )
                    SPEAK("Please give the Camera Access")
                }
                else {
                    val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                    if(intent.resolveActivity(packageManager) != null){
                        startActivityForResult(intent,121)
                    }
                    SPEAK("Video recording is successfully started")
                }
            }

            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("torch","on"))) {
                val isFlashAvailable = applicationContext.packageManager
                    .hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
                if(!isFlashAvailable){
                    SPEAK("Sorry your mobile don't have flash light")
                }
                else{
                    try {
                        cameraId = camMan.cameraIdList[0]
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                    torchMode = true
                    switchFlashLight(torchMode)
                }
            }

            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("torch","off"))) {
                if (torchMode){
                    torchMode = false
                    switchFlashLight(torchMode)
                }
            }

            if(result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("My","Location"))){
                checkLocationPermission()
            }

            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("audio","recording","start"))){
                startAudioRecording()
            }
            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("audio","recording","stop"))){
                stopAudioRecording()
            }
            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("pause","recording","audio"))){
                pauseAudioRecording()
            }
            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("resume","recording","audio"))){
                resumeRocording()
            }
            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("play","recorded","audio"))){
                startPlaying()
            }

            if(result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("detect","objects"))){
                val intent = Intent(this, Preview::class.java)
                startActivity(intent)
            }
            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("find","answer"))){
                val queue = Volley.newRequestQueue(this)
                val url = " https://en.m.wikipedia.org/wiki/Mona_Lisa"

                val stringRequest = StringRequest(
                    Request.Method.GET, url,
                    { response ->
                        val response = "Response is: ${response.substring(0, 500)}"
                        Log.i("response",response)
                    },
                    { SPEAK("It did not work") })
                queue.add(stringRequest)
            }

            if(makeCall){
                phoneNumber = result?.get(0).toString()
                var contact: ContactModel? = null
                try {
                    contact = checkName(phoneNumber!!)!!
                }catch (e: Exception){
                    Log.i("error_msg",e.message.toString())
                }finally {
                    if(contact != null){
                        Timer().schedule(1000){
                            phoneNumber = contact.mobileNumber
                            SPEAK("your given contact name is ${contact.name} and number is $phoneNumber")
                        }
                    }else{
                        Timer().schedule(3500){
                            SPEAK("your given phone number is $phoneNumber")
                        }
                    }
                    Timer().schedule(6000){
                        SPEAK("Is this number correct")
                    }
                    Timer().schedule(13000){
                        askSpeechInput()
                        numbervalidity = true
                    }
                    makeCall = false
                }
            }
            if(numbervalidity){
                if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("yes"))){
                    val callIntent = Intent(Intent.ACTION_CALL)
                    callIntent.data = Uri.parse("tel:$phoneNumber")
                    startActivity(callIntent)
                }
                numbervalidity = false
            }
            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("make a","call"))){
                if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE),121)
                }else if(ActivityCompat.checkSelfPermission(this,Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS),27)
                }
                else{
                    SPEAK("Please provide the contact number or tell me the contact name")
                    Timer().schedule(4000){
                        askSpeechInput()
                        makeCall = true
                    }
                }
            }

            if(getName){
                val contactName = result?.get(0).toString()
                saveContact(contactName,phoneNumber)
                SPEAK("Your contact successfully added")
                getName = false
            }

            if(getContact){
                phoneNumber = result?.get(0).toString()
                SPEAK("Please tell me the contact's name")
                Timer().schedule(3000){
                    askSpeechInput()
                    getContact = false
                    getName = true
                }
            }

            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("save","contact"))){
                if(ActivityCompat.checkSelfPermission(this,Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_CONTACTS),131)
                }else{
                    SPEAK("Please tell me the contact number")
                    Timer().schedule(3000){
                        askSpeechInput()
                        getContact = true
                    }
                }
            }

            if (result?.get(0).toString().containsAllOfIgnoreCase(arrayListOf("search"))){
                val text = result?.get(0).toString()
                val intent = Intent(this,web_activity::class.java)
                intent.putExtra("quarry",text)
                startActivity(intent)
            }
        }

        if(requestCode == 121 && resultCode == RESULT_OK) {
            //get data from uri
            val videoUri = data?.data
            saveVideo(videoUri!!)
            val scale = resources.displayMetrics.density
            val dpWidthInPx = (350 * scale).toInt()
            val dpHeightInPx = (350 * scale).toInt()
            binding.imageView2.layoutParams.width = 0
            binding.imageView2.layoutParams.height = 0
            binding.videoView.layoutParams.height = dpHeightInPx
            binding.videoView.layoutParams.width = dpWidthInPx
            binding.videoView.setVideoURI(videoUri)
            binding.videoView.start()
        }

        if (requestCode == 101) {
            val scale = resources.displayMetrics.density
            val dpWidthInPx = (350 * scale).toInt()
            val dpHeightInPx = (350 * scale).toInt()
            binding.videoView.layoutParams.width = 0
            binding.videoView.layoutParams.height = 0
            binding.imageView2.layoutParams.height = dpHeightInPx
            binding.imageView2.layoutParams.width = dpWidthInPx
            binding.videoView.pause()
            val img = data?.getParcelableExtra<Bitmap>("data")
            binding.imageView2.setImageBitmap(img)
        }
    }


    private fun String.containsAllOfIgnoreCase(keywords:List<String>): Boolean{
        for(keyword in keywords){
            if (!this.contains(keyword,true)) {
                return false
            }
        }
        return  true
    }

    private fun askSpeechInput(){
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault())
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Say Something please!")
        try {
            this.startActivityForResult(i,RQ_CODE)
        } catch (e: Exception) {
            // on below line we are displaying error message in toast
            Toast
                .makeText(
                    this@MainActivity, " " + e.message,
                    Toast.LENGTH_SHORT
                )
                .show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 111 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            SPEAK("Permission Granted")
        }

        if(requestCode == 11 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            SPEAK("Permission Granted")
        }
        if(requestCode == 102 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            SPEAK("Permission Granted")
        }
    }

    private fun saveVideo(videoUri: Uri){
        val uriHelper = URIPathHelper()
        val filepath = uriHelper.getPath(this,videoUri)
        Log.i("file_name" , filepath!!)
    }

    private fun SPEAK(text: String){
        tts = TextToSpeech(applicationContext) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale.getDefault()
                tts.setSpeechRate(1.0f)
                tts.speak(text, TextToSpeech.QUEUE_ADD, null)
            }
        }
    }

    private fun switchFlashLight(status: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                camMan.setTorchMode(cameraId, status)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun startAudioRecording(){
        mediaRecorder = MediaRecorder().apply {
            val simpleDateFormat = SimpleDateFormat("yyyy.MM.MM.DD_hh.mm.ss")
            val date: String = simpleDateFormat.format(Date())
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            file = "$dir/audio_record_$date.mp3"
            setOutputFile(file)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            try {
                prepare()
                state = true
                SPEAK("Audio recording is successfully started")
            } catch (e: IOException) {
                SPEAK("Failed to start audio recording")
            }
            start()
        }
    }

    private fun stopAudioRecording(){
        if(state){
            mediaRecorder?.stop()
            mediaRecorder?.release()
            state = false
        }
        else{
            SPEAK("You are not started audio recording yet")
        }
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @TargetApi(Build.VERSION_CODES.N)
    private fun pauseAudioRecording(){
        if(state){
            if(!recordingStopped){
                mediaRecorder?.pause()
                recordingStopped = true
                SPEAK("Audio recording paused")
            }
            else{
                resumeRocording()
            }
        }
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @TargetApi(Build.VERSION_CODES.N)
    private fun resumeRocording(){
        mediaRecorder?.resume()
        recordingStopped = false
        SPEAK("Audio recording started again")
    }

    private fun startPlaying() {
        player = MediaPlayer().apply {
            try {
                setDataSource(file)
                prepare()
                start()
            } catch (e: IOException) {
                SPEAK("Unable to play the recording audio")
            }
        }
    }


    //Location finder
    private fun checkLocationPermission(){
        if(ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),101)
        }
        else{
            checkGps()
        }
    }

    private  fun checkGps(){
        locationRequest = LocationRequest.create()
        locationRequest.priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = 5000
        locationRequest.fastestInterval = 2000

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        builder.setAlwaysShow(true)

        val result = LocationServices.getSettingsClient(
            this.applicationContext
        ).checkLocationSettings(builder.build())

        result.addOnCompleteListener{task ->
            try{
                val response = task.getResult(
                    ApiException::class.java
                )
                getUserLocation()
            }catch (e : ApiException){
                //when GPS is off
                e.printStackTrace()

                when(e.statusCode){
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try{
                        val resolveApiException = e as ResolvableApiException
                        resolveApiException.startResolutionForResult(this,200)
                    }catch (sendIntentException : IntentSender.SendIntentException){
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        //when setting is unavailable
                    }
                }
            }
        }
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationProviderClient.lastLocation.addOnCompleteListener{ task ->
            val location = task.result
            if(location != null){
                try {
                    val geoCoder = Geocoder(this, Locale.getDefault())
                    val address = geoCoder.getFromLocation(location.latitude,location.longitude,1)

                    val addressLine = address[0].getAddressLine(0)
                    Timer().schedule(2000){
                        SPEAK("Your current Location is $addressLine")
                    }
                }catch (e: IOException){
                }
            }
        }
    }

    @SuppressLint("Range")
    private fun getContactList(): ArrayList<ContactModel> {
        val contactsList = ArrayList<ContactModel>()
        val cr: ContentResolver = contentResolver
        val cur: Cursor? = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            null, null, null, null
        )
        if ((cur?.count ?: 0) > 0) {
            while (cur != null && cur.moveToNext()) {
                val id: String = cur.getString(
                    cur.getColumnIndex(ContactsContract.Contacts._ID)
                )
                val name: String? = cur.getString(
                    cur.getColumnIndex(
                        ContactsContract.Contacts.DISPLAY_NAME
                    )
                )
                if (cur.getInt(
                        cur.getColumnIndex(
                            ContactsContract.Contacts.HAS_PHONE_NUMBER
                        )
                    ) > 0
                ) {
                    val pCur: Cursor? = cr.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", arrayOf(id), null
                    )
                    while (pCur!!.moveToNext()) {
                        val phoneNo: String = pCur.getString(
                            pCur.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                            )
                        )
                        Log.i("name", "Name: $name")
                        Log.i("Ph No", "Phone Number: $phoneNo")
                        contactsList.add(ContactModel(name,phoneNo))
                    }
                    pCur.close()
                }
            }
        }
        cur?.close()
        return contactsList
    }

    private fun checkName(name: String): ContactModel? {
        val contactsList: ArrayList<ContactModel> = getContactList()
        for (contact in contactsList){
            if(contact.name?.containsAllOfIgnoreCase(listOf(name)) == true){
                return contact
            }
        }
        return null
    }

    private fun saveContact(displayName: String?, number: String?){
        val ops = ArrayList<ContentProviderOperation>()

        ops.add(
            ContentProviderOperation.newInsert(
                ContactsContract.RawContacts.CONTENT_URI
            )
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        if (displayName != null) {
            ops.add(ContentProviderOperation.newInsert(
                ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    displayName).build())
        }

        if (number != null) {
            ops.add(ContentProviderOperation.
            newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build())
        }
        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Exception: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

}

