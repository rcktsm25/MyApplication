package com.example.myapplication

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

/**
 * LoginActivity: Módulo de autenticación híbrido.
 * Soporta Firebase Authentication (en la nube) con fallback a sesión local / modo invitado.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("RocketSettings", MODE_PRIVATE)
        auth = FirebaseAuth.getInstance()

        // 1. Comprobar si ya existe una sesión persistente activa (Firebase o SharedPreferences)
        val currentUser = auth.currentUser
        val isRemembered = prefs.getBoolean("is_logged_in", false)

        if (isRemembered && (currentUser != null || prefs.getString("logged_username", null) != null)) {
            navigateToMain()
            return
        }

        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.loginRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupActions()
    }

    private fun setupActions() {
        // Enviar con la tecla "Done" del teclado
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                performLogin()
                true
            } else {
                false
            }
        }

        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.btnGuest.setOnClickListener {
            performGuestLogin()
        }
    }

    /**
     * Proceso de autenticación con Firebase Auth:
     * - Si el identificador no tiene formato de correo, se le añade el dominio local "@rocketplayer.app".
     * - Intenta iniciar sesión; si el usuario no existe en Firebase, lo registra automáticamente.
     * - Si ocurre un error de red, permite acceso con sesión local para no bloquear la app.
     */
    private fun performLogin() {
        val rawInput = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (rawInput.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_error_empty))
            return
        }

        if (password.length < 4) {
            showError(getString(R.string.login_error_invalid))
            return
        }

        hideError()
        setLoadingState(true)

        // Normalizar entrada a formato de correo compatible con Firebase
        val email = if (rawInput.contains("@")) rawInput else "$rawInput@rocketplayer.app"
        val displayName = rawInput.substringBefore("@")
        val remember = binding.cbRememberMe.isChecked

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                onAuthSuccess(displayName, remember)
            }
            .addOnFailureListener { exception ->
                // Si el usuario no existe, crearlo automáticamente en Firebase
                if (exception is FirebaseAuthInvalidUserException) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            onAuthSuccess(displayName, remember)
                        }
                        .addOnFailureListener { createEx ->
                            handleAuthFailure(displayName, remember, createEx)
                        }
                } else if (exception is FirebaseAuthInvalidCredentialsException) {
                    setLoadingState(false)
                    showError("Contraseña incorrecta para este usuario.")
                } else {
                    // Posible error de red o timeout -> fallback local
                    handleAuthFailure(displayName, remember, exception)
                }
            }
    }

    private fun onAuthSuccess(username: String, remember: Boolean) {
        setLoadingState(false)
        prefs.edit().apply {
            putBoolean("is_logged_in", remember)
            putString("logged_username", username)
            apply()
        }

        val welcomeMsg = getString(R.string.login_welcome, username)
        Toast.makeText(this, welcomeMsg, Toast.LENGTH_SHORT).show()
        navigateToMain()
    }

    private fun handleAuthFailure(username: String, remember: Boolean, ex: Exception) {
        setLoadingState(false)
        // Fallback resiliente: autenticar localmente y notificar
        prefs.edit().apply {
            putBoolean("is_logged_in", remember)
            putString("logged_username", username)
            apply()
        }
        Toast.makeText(this, "Modo sin conexión activado para: $username", Toast.LENGTH_SHORT).show()
        navigateToMain()
    }

    /**
     * Modo Invitado: Acceso anónimo en Firebase con fallback local.
     */
    private fun performGuestLogin() {
        setLoadingState(true)
        val guestName = "INVITADO"

        auth.signInAnonymously()
            .addOnCompleteListener {
                setLoadingState(false)
                prefs.edit().apply {
                    putBoolean("is_logged_in", false)
                    putString("logged_username", guestName)
                    apply()
                }
                val welcomeMsg = getString(R.string.login_welcome, guestName)
                Toast.makeText(this, welcomeMsg, Toast.LENGTH_SHORT).show()
                navigateToMain()
            }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnGuest.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "AUTENTICANDO..." else getString(R.string.login_btn_submit)
    }

    private fun showError(message: String) {
        binding.tvLoginError.text = message
        binding.tvLoginError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvLoginError.visibility = View.GONE
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
