package fr.jo.pokedex.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val imagePath: String,
    val audioPath: String,
)

@Entity(
    tableName = "alias",
    foreignKeys = [
        ForeignKey(
            entity = PokemonEntity::class,
            parentColumns = ["id"],
            childColumns = ["pokemonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pokemonId"), Index("normalized")],
)
data class AliasEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val pokemonId: Int,
    val alias: String,
    val normalized: String,
)

@Entity(tableName = "metadata")
data class MetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)
