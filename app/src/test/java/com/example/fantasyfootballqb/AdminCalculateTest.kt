package com.example.fantasyfootballqb

import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.WeekStats
import com.example.fantasyfootballqb.repository.FireStoreRepository
import com.example.fantasyfootballqb.ui.viewmodel.AdminViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminCalculateTest {

    private lateinit var mockRepository: FireStoreRepository
    private lateinit var viewModel: AdminViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Creiamo il finto repository
        mockRepository = mockk(relaxed = true)

        // Passiamo il finto repository al ViewModel
        viewModel = AdminViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun calculationMissingData() = runTest {
        val testWeek = 5

        // Partita 1: partita giocata
        val game1 = mockk<Game> {
            every { id } returns "game_1"
            every { squadraCasa } returns "Eagles"
            every { squadraOspite } returns "Cowboys"
            every { partitaGiocata } returns true
            every { partitaCalcolata } returns false
        }

        // Partita 2: partita giocata
        val game2 = mockk<Game> {
            every { id } returns "game_2"
            every { squadraCasa } returns "Chiefs"
            every { squadraOspite } returns "Giants"
            every { partitaGiocata } returns true
            every { partitaCalcolata } returns false
        }

        // Partita 3: partita non giocata
        val game3 = mockk<Game> {
            every { id } returns "game_3"
            every { squadraCasa } returns "Packers"
            every { squadraOspite } returns "Raiders"
            every { partitaGiocata } returns false
            every { partitaCalcolata } returns false
        }

        // Diciamo al repository finto di restituire queste 3 partite quando viene chiesta la week 5
        coEvery { mockRepository.getGamesForWeek(testWeek) } returns listOf(game1, game2, game3)

        // Diciamo al repository di restituire statistiche finte per G1 e G2, ma NESSUNA per G3
        val dummyStats = listOf(mockk<WeekStats>())
        coEvery { mockRepository.getWeekStatsForGame("game_1") } returns dummyStats
        coEvery { mockRepository.getWeekStatsForGame("game_2") } returns dummyStats
        coEvery { mockRepository.getWeekStatsForGame("game_3") } returns emptyList() // Niente voti ai QB

        // L'admin preme il tasto per calcolare la week
        viewModel.calculateWeek(testWeek)

        advanceUntilIdle()

        val errorMsg = viewModel.error.value

        //verifica errore
        assertTrue(errorMsg!!.contains("Impossibile calcolare:"))
        assertTrue(errorMsg.contains("Packers vs Raiders: Non giocata"))

    }
}