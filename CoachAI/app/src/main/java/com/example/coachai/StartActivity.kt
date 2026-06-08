package com.example.coachai

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity inicial da aplicação.
 * Permite ao utilizador iniciar o processo de análise de exercícios.
 */
class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Associa esta activity ao respetivo layout XML
        setContentView(R.layout.activity_start)

        // Referência ao botão de início
        val btnIniciar = findViewById<Button>(R.id.btnIniciar)

        // Abre a MainActivity quando o utilizador pressiona o botão
        btnIniciar.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}