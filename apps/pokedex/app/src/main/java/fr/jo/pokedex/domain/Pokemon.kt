package fr.jo.pokedex.domain

data class Pokemon(
    val id: Int,
    val name: String,
    val imagePath: String,
    val audioPath: String,
) {
    val number: String get() = "n° %03d".format(id)
}
