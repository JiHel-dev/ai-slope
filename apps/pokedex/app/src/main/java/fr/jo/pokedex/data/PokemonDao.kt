package fr.jo.pokedex.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PokemonDao {
    @Query("SELECT * FROM pokemon ORDER BY id")
    suspend fun allPokemon(): List<PokemonEntity>

    @Query("SELECT * FROM alias")
    suspend fun allAliases(): List<AliasEntity>

    @Query("SELECT value FROM metadata WHERE `key` = :key")
    suspend fun metadata(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMetadata(entry: MetadataEntity)

    @Insert
    suspend fun insertPokemon(pokemon: List<PokemonEntity>)

    @Insert
    suspend fun insertAliases(aliases: List<AliasEntity>)

    @Query("DELETE FROM pokemon")
    suspend fun deleteAllPokemon()

    @Transaction
    suspend fun replaceAll(
        pokemon: List<PokemonEntity>,
        aliases: List<AliasEntity>,
        sourceKey: String,
    ) {
        deleteAllPokemon()
        insertPokemon(pokemon)
        insertAliases(aliases)
        putMetadata(MetadataEntity(KEY_SOURCE, sourceKey))
    }

    companion object {
        const val KEY_SOURCE = "source"
    }
}
