package com.example.game

import kotlinx.coroutines.flow.Flow

class GameRepository(private val gameSaveDao: GameSaveDao) {
    val saveStateFlow: Flow<GameSaveState?> = gameSaveDao.getSaveStateFlow()

    suspend fun getSaveStateDirect(): GameSaveState? {
        return gameSaveDao.getSaveStateDirect()
    }

    suspend fun saveState(state: GameSaveState) {
        gameSaveDao.saveGame(state)
    }

    suspend fun clearState() {
        gameSaveDao.clearSave()
    }
}
