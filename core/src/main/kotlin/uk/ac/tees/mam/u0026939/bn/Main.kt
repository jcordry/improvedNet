package uk.ac.tees.mam.u0026939.bn

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.assets.toInternalFile
import ktx.async.KtxAsync
import ktx.graphics.use

class Main : KtxGame<KtxScreen>() {
    override fun create() {
        KtxAsync.initiate()

        addScreen(FirstScreen())
        setScreen<FirstScreen>()
    }
}

class Character(var position: Vector2, private val sprite: Sprite, val speed: Int) {

    fun draw(batch: SpriteBatch) {
        sprite.position.x = position.x - sprite.texture.width / 2f
        sprite.position.y = position.y - sprite.texture.height / 2f
        sprite.draw(batch)
    }

}

data class Message(var id: Int, var posx: Float, var posy: Float)

class FirstScreen : KtxScreen {
    private val playerTexture = Texture("circle.png".toInternalFile(), true).apply { setFilter(Linear, Linear) }
    private val batch = SpriteBatch()
    private lateinit var players: List<Character>
    private val coroutineScope = = CoroutineScope(Dispatchers.IO + Job())
    private val queue = ConcurrentLinkedQueue<Message>()
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private lateinit var touchpad: Touchpad
    private lateinit var stage: Stage
    private lateinit var mapWidth: Float
    private lateinit var mapHeight: Float
    private lateinit var playerID: Int

    override fun show() {
        val camera = OrthographicCamera()
        mapWidth = Gdx.graphics.width.toFloat()
        mapHeight = Gdx.graphics.height.toFloat()
        camera = OrthographicCamera(mapWidth, mapHeight)
        camera.setToOrtho(false)
        viewport = ScreenViewport(camera)
        viewport.update(mapWidth, mapHeight, true)
        initTouchpad()
        // UDP socket
        // send bcast message
        // receive message back
        // TCP socket
        // connect to server
        // receive a message
        // start a coroutine to receive messages in a loop
        // add messages to a queue
    }

    override fun render(delta: Float) {
        logic(delta)
        draw(delta)
    }

    private fun logic(delta: Float) {
        super.show()
        val moveSpeed = delta * players[playerID].speed
        if (Gdx.input.isKeyPressed(Input.Keys.W)) players[playerID].position.y += moveSpeed
        if (Gdx.input.isKeyPressed(Input.Keys.S)) players[playerID].position.y -= moveSpeed
        if (Gdx.input.isKeyPressed(Input.Keys.A)) players[playerID].position.x -= moveSpeed
        if (Gdx.input.isKeyPressed(Input.Keys.D)) players[playerID].position.x += moveSpeed
        players[playerID].position.x += touchpad.knobPercentX * moveSpeed
        players[playerID].position.y += touchpad.knobPercentY * moveSpeed

        // Player to stay inside the screen
        players[playerID].position.x = players[playerID].position.x.coerceIn(playerTexture.width/2f, mapWidth - playerTexture.width/2f)
        players[playerID].position.y = players[playerID].position.y.coerceIn(playerTexture.height/2f, mapHeight - playerTexture.height/2f)

        // clients need to have the same screen size/resolution!

        // poll the messages from the queue

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
        mapWidth = width
        mapHeight = height
    }

    private fun initTouchpad() {
        stage = Stage(viewport, batch)
        val skin = Skin()
        // All this could be in an atlas
        skin.add("background", Texture("touchpad.png"))
        skin.add("knob", Texture("touchpad-knob.png"))
        val touchpadStyle = TouchpadStyle()
        touchpadStyle.background = skin.getDrawable("background")
        touchpadStyle.knob = skin.getDrawable("knob")
        touchpad = Touchpad(10f, touchpadStyle)
        stage.addActor(touchpad)
        Gdx.input.inputProcessor = stage
    }

    override fun dispose() {
        playerTexture.disposeSafely()
        stage.disposeSafely()
        batch.disposeSafely()
    }
}
