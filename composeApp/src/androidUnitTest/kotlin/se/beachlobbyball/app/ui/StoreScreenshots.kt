package se.beachlobbyball.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.auth.SignInMethod
import se.beachlobbyball.app.chat.ChatMessage
import se.beachlobbyball.app.chat.ChatRepository
import se.beachlobbyball.app.friends.Friend
import se.beachlobbyball.app.friends.FriendRequest
import se.beachlobbyball.app.friends.FriendsRepository
import se.beachlobbyball.app.friends.relationTo
import se.beachlobbyball.app.i18n.languageOverride
import se.beachlobbyball.app.i18n.tr
import se.beachlobbyball.app.invites.Invite
import se.beachlobbyball.app.invites.InviteRepository
import se.beachlobbyball.app.lobby.LobbyRepository
import se.beachlobbyball.app.model.Format
import se.beachlobbyball.app.model.Location
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.LobbyType
import se.beachlobbyball.app.model.Mode
import se.beachlobbyball.app.model.Player
import se.beachlobbyball.app.model.Visibility
import se.beachlobbyball.app.profile.ProfileRepository
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Renders the real screens with sample data, for the Google Play listing.
 * Every CI run renders them as a check. To save the images, run:
 *
 *     ./gradlew :composeApp:testDebugUnitTest --tests '*StoreScreenshots*' -Proborazzi.test.record=true
 *
 * They land in composeApp/build/store-screenshots/{en,sv}/, at 1080x1920 (9:16, as Play prefers).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class StoreScreenshots(private val language: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun languages(): List<Array<Any>> = listOf(arrayOf<Any>("en"), arrayOf<Any>("sv"))
    }

    @get:Rule
    val compose = createComposeRule()

    private lateinit var demo: Demo

    @Before
    fun setUp() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Stockholm"))
        languageOverride = language
        demo = Demo()
    }

    @After
    fun tearDown() {
        languageOverride = null
    }

    @Test
    fun publicLobbies() = shot("1-public-lobbies", MainTab.PublicLobbies) { modifier ->
        LobbyListScreen(
            source = flowOf(demo.publicLobbies),
            title = tr("Public lobbies", "Öppna lobbyer"),
            emptyText = "",
            onOpen = {},
            modifier = modifier,
        )
    }

    @Test
    fun lobbyDetails() = shot("2-lobby-details", selected = null) { modifier ->
        DemoLobby(modifier, startOnChat = false)
    }

    @Test
    fun lobbyChat() = shot("3-lobby-chat", selected = null) { modifier ->
        DemoLobby(modifier, startOnChat = true)
    }

    @Test
    fun privateLobbies() = shot("4-private-lobbies", MainTab.PrivateLobbies) { modifier ->
        PrivateLobbiesScreen(
            user = demo.me,
            invites = listOf(demo.invite),
            lobbies = listOf(demo.privateLobby),
            inviteRepository = InviteRepository(),
            onOpen = {},
            modifier = modifier,
        )
    }

    @Test
    fun friends() = shot("5-friends", MainTab.Friends) { modifier ->
        FriendsScreen(
            user = demo.me,
            friends = demo.friends,
            incoming = listOf(demo.friendRequest),
            outgoing = emptyList(),
            friendsRepository = FriendsRepository(),
            profiles = ProfileRepository(),
            modifier = modifier,
        )
    }

    @Test
    fun createLobby() = shot("6-create-lobby", MainTab.CreateLobby) { modifier ->
        CreateLobbyScreen(user = demo.me, repository = LobbyRepository(), onCreated = {}, modifier = modifier)
    }

    @Composable
    private fun DemoLobby(modifier: Modifier, startOnChat: Boolean) {
        LobbyDetailScreen(
            lobbyId = demo.gbcEvening.id,
            user = demo.me,
            repository = LobbyRepository(),
            chat = ChatRepository(),
            blockedIds = emptySet(),
            friends = demo.friends,
            invites = InviteRepository(),
            hasInvite = false,
            relationTo = { uid -> relationTo(uid, demo.me.uid, demo.friends, listOf(demo.friendRequest), emptyList()) },
            onAddFriend = {},
            onBack = {},
            modifier = modifier,
            lobbySource = flowOf(demo.gbcEvening),
            chatMessages = flowOf(demo.messages),
            startOnChat = startOnChat,
        )
    }

    /** Draws [screen] inside the app's tab bar, the way MainScreen does, and saves it. */
    private fun shot(name: String, selected: MainTab?, screen: @Composable (Modifier) -> Unit) {
        compose.setContent {
            BeachTheme {
                Scaffold(
                    bottomBar = {
                        MainNavigationBar(
                            selected = selected,
                            badgeCount = { tab ->
                                when (tab) {
                                    MainTab.PrivateLobbies, MainTab.Friends -> 1
                                    else -> 0
                                }
                            },
                            onSelect = {},
                        )
                    },
                ) { padding -> screen(Modifier.padding(padding)) }
            }
        }
        compose.onRoot().captureRoboImage("build/store-screenshots/$language/$name.png")
    }
}

/** Sample people, lobbies and messages, dated from today so the lobbies are always upcoming. */
internal class Demo {
    private val zone = TimeZone.currentSystemDefault()
    private val today = Clock.System.todayIn(zone)
    private val now = Clock.System.now()

    private fun at(daysAhead: Int, hour: Int, minute: Int = 0) =
        LocalDateTime(today.plus(daysAhead, DateTimeUnit.DAY), LocalTime(hour, minute)).toInstant(zone)

    val me = AppUser(uid = "me", displayName = "Alex", isGuest = false, email = "alex@example.com", method = SignInMethod.Google)

    private val elin = Player("elin", "Elin")
    private val johan = Player("johan", "Johan")
    private val sara = Player("sara", "Sara")
    private val oskar = Player("oskar", "Oskar")
    private val maja = Player("maja", "Maja")
    private val erik = Player("erik", "Erik")
    private val linnea = Player("linnea", "Linnea")
    private val ida = Player("ida", "Ida")
    private val anton = Player("anton", "Anton")
    private val self = Player(me.uid, me.displayName)

    val gbcEvening = Lobby(
        id = "gbc-evening",
        hostId = elin.uid,
        hostName = elin.name,
        location = Location.GBC,
        createdAt = now - 3.hours,
        startsAt = at(1, 18),
        duration = 2.hours,
        type = LobbyType.Mixed,
        mode = Mode.Casual,
        format = Format.TwoVTwo,
        maxPlayers = 4,
        level = tr("Intermediate", "Medel"),
        isCourtBooked = true,
        courtNumber = "3",
        note = tr("Bring a ball if you have one. Beer after!", "Ta med boll om du har. Öl efteråt!"),
        players = listOf(elin, self, johan),
    )

    val publicLobbies = listOf(
        gbcEvening,
        Lobby(
            id = "skatas-morning",
            hostId = oskar.uid,
            hostName = oskar.name,
            location = Location.Skatas,
            createdAt = now - 20.hours,
            startsAt = at(2, 10),
            duration = 90.minutes,
            type = LobbyType.Any,
            mode = Mode.Sparring,
            format = Format.FourVFour,
            maxPlayers = 8,
            players = listOf(oskar, maja, erik, linnea, anton),
        ),
        Lobby(
            id = "askim-women",
            hostId = sara.uid,
            hostName = sara.name,
            location = Location.Other,
            otherLocationName = "Askimsbadet",
            createdAt = now - 26.hours,
            startsAt = at(2, 17, 30),
            duration = 2.hours,
            type = LobbyType.Women,
            mode = Mode.Practice,
            format = Format.TwoVTwo,
            maxPlayers = 4,
            level = tr("Advanced", "Avancerad"),
            players = listOf(sara, ida, maja, linnea),
            waitlist = listOf(elin),
        ),
        Lobby(
            id = "gbc-men",
            hostId = erik.uid,
            hostName = erik.name,
            location = Location.GBC,
            createdAt = now - 5.hours,
            startsAt = at(3, 19),
            duration = 2.hours,
            type = LobbyType.Men,
            mode = Mode.Sparring,
            format = Format.ThreeVThree,
            maxPlayers = 6,
            isCourtBooked = true,
            courtNumber = "5",
            players = listOf(erik, anton),
        ),
        Lobby(
            id = "skatas-sunday",
            hostId = linnea.uid,
            hostName = linnea.name,
            location = Location.Skatas,
            createdAt = now - 2.hours,
            startsAt = at(5, 13),
            duration = 3.hours,
            type = LobbyType.Mixed,
            mode = Mode.Casual,
            players = listOf(linnea, ida),
        ),
    )

    val privateLobby = Lobby(
        id = "private-crew",
        hostId = me.uid,
        hostName = me.displayName,
        location = Location.GBC,
        createdAt = now - 1.hours,
        startsAt = at(4, 18, 30),
        duration = 2.hours,
        type = LobbyType.Mixed,
        format = Format.TwoVTwo,
        maxPlayers = 4,
        visibility = Visibility.Private,
        isCourtBooked = true,
        courtNumber = "2",
        players = listOf(self, elin, johan),
        joinCode = "K7PX4M",
    )

    val invite = Invite(
        lobbyId = "skatas-friday",
        fromId = oskar.uid,
        fromName = oskar.name,
        toId = me.uid,
        locationName = Location.Skatas.label,
        startsAt = at(3, 17),
        endsAt = at(3, 19),
        sentAt = now - 30.minutes,
    )

    private val crew = tr("GBC crew", "GBC-gänget")
    private val sparring = tr("Sparring", "Sparring")
    private val mixed = tr("Mixed team", "Mixedlaget")

    val friends = listOf(
        Friend(elin.uid, elin.name, tags = listOf(crew, mixed)),
        Friend(johan.uid, johan.name, tags = listOf(crew)),
        Friend(oskar.uid, oskar.name, tags = listOf(sparring)),
        Friend(sara.uid, sara.name, tags = listOf(mixed)),
        Friend(maja.uid, maja.name, tags = listOf(crew, sparring)),
        Friend(anton.uid, anton.name),
    )

    val friendRequest = FriendRequest(fromId = "viktor", fromName = "Viktor", toId = me.uid, toName = me.displayName)

    val messages = listOf(
        ChatMessage("m1", elin.uid, elin.name, tr("Court 3 is booked from 18. See you there!", "Bana 3 är bokad från 18. Ses där!"), now - 2.hours),
        ChatMessage("m2", johan.uid, johan.name, tr("Nice, I'll bring a ball", "Grymt, jag tar med boll"), now - 100.minutes),
        ChatMessage("m3", me.uid, me.displayName, tr("Count me in. One spot left if anyone wants it", "Jag är med. En plats kvar om någon vill"), now - 45.minutes),
        ChatMessage("m4", elin.uid, elin.name, tr("Shared it in the group chat", "Delade i gruppchatten"), now - 20.minutes),
    )
}
