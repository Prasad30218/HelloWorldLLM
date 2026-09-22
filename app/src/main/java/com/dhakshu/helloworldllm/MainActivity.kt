package com.dhakshu.helloworldllm

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.util.concurrent.Executors
import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val modelPath = "/data/local/tmp/gemma3-1b-it.task"
    private val executor = Executors.newSingleThreadExecutor()
    private var llm: LlmInference? = null
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent
    private lateinit var textToSpeech: TextToSpeech

    private lateinit var inputText: EditText
    private lateinit var translateButton: Button
    private lateinit var outputText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        inputText = findViewById(R.id.inputText)
        translateButton = findViewById(R.id.translateButton)
        outputText = findViewById(R.id.outputText)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
            }
        }
        val speakEnglishButton = findViewById<Button>(R.id.speakEnglishButton)

        speakEnglishButton.setOnClickListener {
            speakEnglish()
        }

        val speakButton = findViewById<Button>(R.id.speakButton)
        speakButton.setOnClickListener {
            startSpeechRecognition()
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: Bundle?) {
                val matches =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (!matches.isNullOrEmpty()) {
                    inputText.setText(matches[0])
                }
            }

            override fun onError(error: Int) {
                outputText.text = "Speech recognition error: $error"
            }

            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Load the model off the main thread (takes a few seconds)
        executor.execute {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()
                llm = LlmInference.createFromOptions(applicationContext, options)
                runOnUiThread {
                    outputText.text = "Model ready. Type or speak a phrase."
                    translateButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread { outputText.text = "Model load failed: ${e.message}" }
            }
        }

        translateButton.setOnClickListener {
            val phrase = inputText.text.toString().trim()

            if (phrase.isEmpty()) {
                outputText.text = "Please enter a Telugu phrase."
                return@setOnClickListener
            }

            translateButton.isEnabled = false
            outputText.text = "Translating..."

            executor.execute {
                val result = translatePhrase(phrase)

                runOnUiThread {
                    outputText.text = result
                    translateButton.isEnabled = true
                }
            }
        }
    }

    private fun startSpeechRecognition() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
            return
        }

        speechRecognizer.startListening(speechIntent)
    }

    private fun speakEnglish() {
        val text = outputText.text.toString()

        if (text.isNotBlank()) {
            textToSpeech.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "translation"
            )
        }
    }

    private fun translatePhrase(phrase: String): String {

        val normalized = phrase
            .lowercase()
            .trim()
            .replace(Regex("[.,!?]+$"), "")
            .replace(Regex("\\s+"), " ")
        android.util.Log.d("TRANSLATION_DEBUG", "Original=[$phrase]")
        android.util.Log.d("TRANSLATION_DEBUG", "Normalized=[$normalized]")
        val knownTranslations = mapOf(
            // Basic pronouns
            "nenu" to "I",
            "naaku" to "to me / I have",
            "naku" to "to me / I have",
            "nuvvu" to "you",
            "meeru" to "you",

            // Question words
            "em" to "what",
            "ekkada" to "where",
            "ekkadiki" to "where / to where",
            "evaru" to "who",
            "enduku" to "why",
            "ela" to "how",
            "eppudu" to "when",

            // Common nouns
            "college" to "college",
            "illu" to "house / home",
            "inti" to "home",
            "intiki" to "to home",
            "coffee" to "coffee",

            // Common verbs
            "velthunna" to "going",
            "velthunnanu" to "going",
            "vellutunna" to "going",
            "vellutunnanu" to "going",
            "chestunnav" to "are doing",
            "chestunnavu" to "are doing",
            "kavali" to "want",

            // Common descriptive words
            "aakali" to "hunger",
            "chala" to "very / a lot",
            "baaga" to "well / very",
            "ardham" to "understanding",
            "kaaledu" to "did not happen / could not",
            
            //half sentences
            "em chestunnav" to "What are you doing?",
            "em chestunnavu" to "What are you doing?",
            "ekkadiki velthunnav" to "Where are you going?",
            "ekkadiki velthunnavu" to "Where are you going?",
            "ekkadiki vellav" to "Where did you go?",
            "ekkadiki vellavu" to "Where did you go?",
            "naku teliyadu" to "I don't know.",
            "naaku teliyadu" to "I don't know.",
            "naku ardham kaledu" to "I did not understand.",
            "naaku ardham kaledu" to "I did not understand.",
            "naku coffee kavali" to "I want coffee.",
            "naaku coffee kavali" to "I want coffee.",

            // Full sentences
            "nenu college ki velthunna" to "I am going to college.",
            "naku chala aakali ga undi" to "I am very hungry.",
            "nuvvu em chestunnav" to "What are you doing?",
            "nuvvu em chestunnavu" to "What are you doing?",
            "nenu intiki velthunna" to "I am going home.",
            "meeru ekkadiki velthunnaru" to "Where are you going?",
            "naku coffee kavali" to "I want coffee.",
            "meeru ela unnaru" to "How are you?",
            "naaku ardham kaaledu" to "I did not understand.",
            "nenu college ki vellutunna" to "I am going to college.",
            "nenu intiki vellutunna" to "I am going home."
        )

        val knownResult = knownTranslations[normalized]

        if (knownResult != null) {
            return knownResult
        }

        return try {
            llm?.generateResponse(buildPrompt(phrase))?.trim()
                ?: "Model not loaded."
        } catch (e: Exception) {
            "Translation error: ${e.message}"
        }
    }

    private fun buildPrompt(input: String): String {
        return """
    You are a Telugu-to-English translator.

    The user writes Telugu using English letters (Telugu transliteration).

    Translate the Telugu sentence into natural English.

    Important rules:
    - Treat the input as Telugu transliteration, NOT as an English sentence.
    - Do not say that the sentence is written in English.
    - Do not explain the translation.
    - Return ONLY the English translation.
    - Preserve the meaning of the complete sentence.

    Known Telugu words:
    - nenu = I
    - naaku / naku = I have / to me
    - nuvvu = you
    - meeru = you
    - em = what
    - ekkadiki = where / to where
    - college = college
    - inti / intiki = home / to home
    - velthunna = am going
    - velthunnanu = am going
    - aakali = hunger
    - chala = very / a lot
    - ga undi = am feeling / is

    Examples:
    Telugu: nenu college ki velthunna
    English: I am going to college.

    Telugu: naku chala aakali ga undi
    English: I am very hungry.

    Telugu: nuvvu em chestunnav
    English: What are you doing?

    Telugu: nenu intiki velthunna
    English: I am going home.

    Telugu: meeru ekkadiki velthunnaru
    English: Where are you going?

    Now translate this Telugu transliteration:
    $input
    """.trimIndent()
    }
    
    override fun onDestroy() {
        speechRecognizer.destroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        llm?.close()
        executor.shutdown()
        super.onDestroy()
    }
}