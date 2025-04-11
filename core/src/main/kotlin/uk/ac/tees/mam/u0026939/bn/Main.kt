package uk.ac.tees.mam.u0026939.bn

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.viewport.Viewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.assets.toInternalFile
import ktx.async.KtxAsync
import ktx.graphics.use
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue

class Main : KtxGame<KtxScreen>() {
    override fun create() {
        KtxAsync.initiate()

        addScreen(GameScreen())
        setScreen<GameScreen>()
    }
}

// Purposely simple message class
// Note: this does not contain an action enum
data class Message(var id: Int, var posX: Float, var posY: Float)

class Character(
    private val sprite: Sprite,
    val speed: Int
) {

    var posX: Float = 0f
        private set // don't overwrite the position from outside the class
    var posY: Float = 0f
        private set // don't overwrite the position from outside the class


    fun update(message: Message) { // from the server or local
        posX = message.posX
        posY = message.posY
    }

    fun draw(batch: SpriteBatch) { // I like the position to be in the middle of the sprite
        sprite.x = posX - sprite.texture.width / 2f
        sprite.y = posY - sprite.texture.height / 2f
        sprite.draw(batch)
    }
}

class GameScreen : KtxScreen {
    private val playerTexture = Texture("circle.png".toInternalFile(), true).apply { setFilter(Linear, Linear) }
    private val batch = SpriteBatch()
    private lateinit var players: List<Character>
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val queue = ConcurrentLinkedQueue<Message>() // incoming messages
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private lateinit var touchpad: Touchpad
    private lateinit var stage: Stage
    private var mapWidth: Float = 2400f
    private var mapHeight: Float = 1080f
    private var playerID: Int = 0
    private lateinit var tcpSocket: Socket
    private val playerColours = listOf(
        com.badlogic.gdx.graphics.Color.WHITE,
        com.badlogic.gdx.graphics.Color.GREEN,
        com.badlogic.gdx.graphics.Color.BLUE,
    )
    private val spawnPoints = listOf(
        Vector2(700f, 500f), // Made with 800x600 screen dimensions in mind
        Vector2(700f, 100f),
        Vector2(100f, 500f),
    )

    // We set things up
    override fun show() {
        camera = OrthographicCamera()
        mapWidth = Gdx.graphics.width.toFloat()
        mapHeight = Gdx.graphics.height.toFloat()
        camera = OrthographicCamera(mapWidth, mapHeight)
        camera.setToOrtho(false)
        viewport = ScreenViewport(camera)
        viewport.update(mapWidth.toInt(), mapHeight.toInt(), true)
        initTouchpad()

        // Networking stuff
        // UDP socket
        val datagramSocket = DatagramSocket()
        // send broadcast message; HERE, we are using the localhost address, this is not really a broadcast
        datagramSocket.send(DatagramPacket("Hello".toByteArray(), 5, InetAddress.getByName("localhost"), 4301))
        // receive message back
        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        datagramSocket.receive(packet)
        // TCP socket
        // connect to server
        tcpSocket = Socket(packet.address, 4300)
        // receive a message with an int from the server: this is our player ID
        val inputStream = tcpSocket.getInputStream()
        var bytes = ByteArray(4)
        inputStream.read(bytes)
        playerID = bytes[0].toInt()
        // Create the players
        players = List(3) { i ->
            // Note: we don't know the number of players yet
            // We assign a spawn point to each player
            // We could also tweak the protocol to send the spawn points
            val position = spawnPoints[i]
            val sprite = Sprite(playerTexture)
            sprite.color = playerColours[i]
            val character = Character(sprite, 300)
            character.update(Message(i, position.x, position.y)) // update the position
            character // return the character
        }
        // start a coroutine to receive messages in a loop
        // add messages to a queue
        coroutineScope.launch {
            while (true) { // while true is not safe: this does not terminate
                bytes = ByteArray(1024)
                val length = inputStream.read(bytes) // Blocking operation
                if (length > 0) {
                    val message = String(bytes, 0, length).split(",")
                    if (message.size == 3) {
                        val id = message[0].toInt()
                        val posX = message[1].toFloat()
                        val posY = message[2].toFloat()
                        // Note we are not handling the message here.
                        // The message could have some impact on the game.
                        // We want to deal with the message in the logic method.
                        queue.add(Message(id, posX, posY))
                    }
                }
            }
        }
    }

    override fun render(delta: Float) {
        logic(delta)
        draw(delta)
    }

    private fun logic(delta: Float) {
        super.show()
        val moveSpeed = delta * players[playerID].speed
        // make a note of the old position
        val oldPosition = Vector2(players[playerID].posX, players[playerID].posY)
        // where the new position is going to be
        val newPosition = Vector2(players[playerID].posX, players[playerID].posY)
        if (Gdx.input.isKeyPressed(Input.Keys.W)) newPosition.y += moveSpeed
        if (Gdx.input.isKeyPressed(Input.Keys.A)) newPosition.x -= moveSpeed
        if (Gdx.input.isKeyPressed(Input.Keys.S)) newPosition.y -= moveSpeed
        if (Gdx.input.isKeyPressed(Input.Keys.D)) newPosition.x += moveSpeed
        newPosition.x += touchpad.knobPercentX * moveSpeed
        newPosition.y += touchpad.knobPercentY * moveSpeed

        // Player to stay inside the screen
        newPosition.x = newPosition.x.coerceIn(playerTexture.width/2f, mapWidth - playerTexture.width/2f)
        newPosition.y = newPosition.y.coerceIn(playerTexture.height/2f, mapHeight - playerTexture.height/2f)

        // send message to server if required; if the position has changed, let's do things
        if (oldPosition.x != newPosition.x || oldPosition.y != newPosition.y) {
            val message = Message(playerID, newPosition.x, newPosition.y)
            players[playerID].update(message) // update the local player
            // Note: this is not a binary message. Use this for debugging.
            // Switch to binary messages for production.
            val bytes = "${playerID},${newPosition.x},${newPosition.y}".toByteArray()
            coroutineScope.launch { // Could be done without the coroutine
                tcpSocket.getOutputStream().write(bytes) // Blocking operation
                tcpSocket.getOutputStream().flush()
            }
        }

        // ¡clients need to have the same screen size!

        // poll the messages from the queue
        while (queue.isNotEmpty()) {
            val queueMsg = queue.poll()
            if (queueMsg != null) {
                players[queueMsg.id].update(queueMsg) // Note if more players are added, this will not work
            }
        }
    }

    private fun draw(delta: Float) {
        clearScreen(red = 0.7f, green = 0.7f, blue = 0.7f)
        stage.act(delta)
        batch.use {
            for (player in players) {
                player.draw(it)
            }
        }
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        viewport.update(width, height)
        camera.update()
        mapWidth = width.toFloat()
        mapHeight = height.toFloat()
    }

    private fun initTouchpad() {
        stage = Stage(viewport, batch)
        val skin = Skin()
        // All this could be in an atlas
        skin.add("touchpad", Texture("touchpad.png"))
        skin.add("touchpad-knob", Texture("touchpad-knob.png"))
        val touchpadStyle = TouchpadStyle()
        touchpadStyle.background = skin.getDrawable("touchpad")
        touchpadStyle.knob = skin.getDrawable("touchpad-knob")
        touchpad = Touchpad(10f, touchpadStyle)
        stage.addActor(touchpad)
        Gdx.input.inputProcessor = stage
    }

    override fun dispose() {
        // More disposal required
        playerTexture.disposeSafely()
        stage.disposeSafely()
        batch.disposeSafely()
    }
}
