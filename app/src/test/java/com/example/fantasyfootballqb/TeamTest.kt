package com.example.fantasyfootballqb

import com.example.fantasyfootballqb.repository.FireStoreRepository
import com.example.fantasyfootballqb.ui.viewmodel.TeamViewModel
import com.google.firebase.auth.FirebaseAuth
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamTest {

    // 1. Prepariamo i "Mock" (oggetti finti) per ingannare il ViewModel
    // "relaxed = true" significa che se il ViewModel chiama funzioni su questi oggetti,
    // essi non crasheranno ma restituiranno valori null o vuoti di default.
    private val mockRepository: FireStoreRepository = mockk(relaxed = true)
    private val mockAuth: FirebaseAuth = mockk(relaxed = true)

    private lateinit var viewModel: TeamViewModel

    @Before
    fun setup() {
        // Le app Android usano Dispatchers.Main per la UI, ma nei test non c'è UI!
        // Sostituiamo il thread della UI con uno speciale per i test.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // Istanziamo il ViewModel passandogli il Firebase finto
        viewModel = TeamViewModel(repository = mockRepository, auth = mockAuth)
    }

    @After
    fun tearDown() {
        // Pulizia finale per non sporcare altri test
        Dispatchers.resetMain()
    }

    @Test
    fun send2Qb() {
        // lista di soli 2 giocatori
        val qbIdsSbagliati = listOf("qb_id_1", "qb_id_2")

        // WL'utente  prova a salvare la formazione
        viewModel.submitFormation(week = 1, qbIds = qbIdsSbagliati)

        // Mi aspetto che il ViewModel generi un messaggio di errore
        val erroreAtteso = "Devi selezionare 3 QB distinti"
        val erroreOttenuto = viewModel.error.value

        assertEquals(erroreAtteso, erroreOttenuto)
    }

    @Test
    fun sendSameQb() {
        // Una lista di 3 giocatori, ma il primo e l'ultimo sono lo stesso
        val qbIdsDuplicati = listOf("id_mahomes", "id_brady", "id_mahomes")

        // L'utente tenta di inviare la formazione al ViewModel
        viewModel.submitFormation(week = 1, qbIds = qbIdsDuplicati)

        // Il ViewModel deve accorgersi dell'errore
        val erroreAtteso = "Devi selezionare 3 QB distinti"
        val erroreOttenuto = viewModel.error.value

        assertEquals(erroreAtteso, erroreOttenuto)

    }
}