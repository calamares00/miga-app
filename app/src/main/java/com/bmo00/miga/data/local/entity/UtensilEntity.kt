package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "utensils")
data class UtensilEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
