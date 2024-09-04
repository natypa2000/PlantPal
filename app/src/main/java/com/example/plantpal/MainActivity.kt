package com.example.plantpal
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.plantpal.databinding.ActivityMainBinding
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, LoginFragment())
                .commit()
        }
    }
}