package com.fjwu.pencil2pexel

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.util.Calendar
import kotlin.random.Random

class NadraVerificationActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var nameText: TextView
    private lateinit var cnicText: TextView
    private lateinit var dobText: TextView
    private lateinit var ageText: TextView
    private lateinit var fatherNameText: TextView
    private lateinit var fatherCnicText: TextView
    private lateinit var postalAddressText: TextView
    private lateinit var residentialAddressText: TextView
    private lateinit var closeButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_nadra_verification)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        loadMockData()
        setupListeners()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        nameText = findViewById(R.id.nameText)
        cnicText = findViewById(R.id.cnicText)
        dobText = findViewById(R.id.dobText)
        ageText = findViewById(R.id.ageText)
        fatherNameText = findViewById(R.id.fatherNameText)
        fatherCnicText = findViewById(R.id.fatherCnicText)
        postalAddressText = findViewById(R.id.postalAddressText)
        residentialAddressText = findViewById(R.id.residentialAddressText)
        closeButton = findViewById(R.id.closeButton)
    }

    private fun loadMockData() {
        val details = generateMockNadraDetails()
        nameText.text = details.name
        cnicText.text = details.cnic
        dobText.text = details.dob
        ageText.text = "${details.age} years"
        fatherNameText.text = details.fatherName
        fatherCnicText.text = details.fatherCnic
        postalAddressText.text = details.postalAddress
        residentialAddressText.text = details.residentialAddress
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        closeButton.setOnClickListener {
            finish()
        }
    }

    private fun generateMockNadraDetails(): NadraDetails {
        val firstNames = listOf("Ayesha", "Fatima", "Sara", "Zainab", "Khadija", "Maryam", "Aisha", "Hira")
        val lastNames = listOf("Khan", "Ahmed", "Ali", "Malik", "Siddiqui", "Riaz", "Rehman", "Hassan")
        val fatherFirstNames = listOf("Muhammad", "Ahmed", "Ali", "Hassan", "Usman", "Bilal", "Hamza", "Imran")

        val cities = listOf(
            "Islamabad" to listOf("F-8/3", "F-10/2", "G-11/1", "I-8/4", "E-7"),
            "Lahore" to listOf("Model Town", "Gulberg", "DHA Phase 5", "Johar Town", "Cantt"),
            "Karachi" to listOf("North Nazimabad", "DHA Phase 6", "Clifton", "Gulshan-e-Iqbal", "PECHS"),
            "Rawalpindi" to listOf("Bahria Town", "Satellite Town", "Saddar", "Chaklala", "PWD"),
            "Peshawar" to listOf("Hayatabad", "University Town", "Cantt", "Saddar", "Board Bazar"),
            "Multan" to listOf("Cantt", "Gulgasht", "Model Town", "Shah Rukn-e-Alam", "Bosan Road")
        )

        val name = "${firstNames.random()} ${lastNames.random()}"
        val fatherName = "${fatherFirstNames.random()} ${lastNames.random()}"
        val cnic = generateCnic()
        val fatherCnic = generateCnic()
        val dob = generateDob()
        val age = calculateAge(dob)
        
        val cityData = cities.random()
        val city = cityData.first
        val area = cityData.second.random()
        val houseNo = Random.nextInt(1, 200)
        val streetNo = Random.nextInt(1, 50)
        
        val postalAddress = "House $houseNo, Street $streetNo, $area, $city"
        
        // 70% chance residential address is same as postal
        val residentialAddress = if (Random.nextFloat() < 0.7f) {
            postalAddress
        } else {
            val differentArea = cityData.second.filter { it != area }.randomOrNull() ?: area
            "House ${Random.nextInt(1, 200)}, Street ${Random.nextInt(1, 50)}, $differentArea, $city"
        }

        return NadraDetails(
            name = name,
            cnic = cnic,
            dob = dob,
            age = age,
            fatherName = fatherName,
            fatherCnic = fatherCnic,
            postalAddress = postalAddress,
            residentialAddress = residentialAddress
        )
    }

    private fun generateCnic(): String {
        val part1 = Random.nextInt(10000, 99999)
        val part2 = Random.nextInt(1000000, 9999999)
        val part3 = Random.nextInt(1, 9)
        return String.format("%05d-%07d-%d", part1, part2, part3)
    }

    private fun generateDob(): String {
        val year = Random.nextInt(1970, 2006)
        val month = Random.nextInt(1, 13)
        val day = Random.nextInt(1, 29)
        return String.format("%02d-%02d-%04d", day, month, year)
    }

    private fun calculateAge(dob: String): Int {
        // Parse DOB (format: DD-MM-YYYY)
        val parts = dob.split("-")
        val birthYear = parts[2].toInt()
        val birthMonth = parts[1].toInt()
        val birthDay = parts[0].toInt()

        val today = Calendar.getInstance()
        val currentYear = today.get(Calendar.YEAR)
        val currentMonth = today.get(Calendar.MONTH) + 1
        val currentDay = today.get(Calendar.DAY_OF_MONTH)

        var age = currentYear - birthYear

        // Adjust if birthday hasn't occurred this year
        if (currentMonth < birthMonth || (currentMonth == birthMonth && currentDay < birthDay)) {
            age--
        }

        return age
    }

    private data class NadraDetails(
        val name: String,
        val cnic: String,
        val dob: String,
        val age: Int,
        val fatherName: String,
        val fatherCnic: String,
        val postalAddress: String,
        val residentialAddress: String
    )
}
