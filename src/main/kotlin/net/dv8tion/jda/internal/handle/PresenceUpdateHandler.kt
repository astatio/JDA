/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.internal.handle

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.ClientType
import net.dv8tion.jda.api.events.user.UserActivityEndEvent
import net.dv8tion.jda.api.events.user.UserActivityStartEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateActivitiesEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateActivityOrderEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateOnlineStatusEvent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.api.utils.data.DataArray
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.entities.EntityBuilder
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.MemberImpl
import net.dv8tion.jda.internal.entities.MemberPresenceImpl
import net.dv8tion.jda.internal.utils.Helpers
import net.dv8tion.jda.internal.utils.JDALogger
import java.util.EnumSet

class PresenceUpdateHandler(
    api: JDAImpl,
) : SocketHandler(api) {
    @Suppress("ReturnCount") // faithfully ported early-return guard chain from the Java original
    protected override fun handleInternally(content: DataObject): Long? {
        // Ignore events for relationships, presences are guild only to us
        if (content.isNull("guild_id")) {
            log.debug("Received PRESENCE_UPDATE without guild_id. Ignoring event.")
            return null
        }
        if (api.getCacheFlags().stream().noneMatch(CacheFlag::isPresence)) {
            return null
        }

        // Do a pre-check to see if this is for a Guild, and if it is,
        // if the guild is currently locked or not cached.
        val guildId = content.getUnsignedLong("guild_id")
        if (getJDA().getGuildSetupController().isLocked(guildId)) {
            return guildId
        }
        val guild = getJDA().getGuildById(guildId) as GuildImpl?
        if (guild == null) {
            getJDA().getEventCache().cache(EventCache.Type.GUILD, guildId, responseNumber, allContent, this::handle)
            EventCache.LOG.debug(
                "Received a PRESENCE_UPDATE for a guild that is not yet cached! GuildId:{} UserId: {}",
                guildId,
                content.getObject("user").get("id"),
            )
            return null
        }

        val presences: CacheView.SimpleCacheView<MemberPresenceImpl>? = guild.getPresenceView()
        if (presences == null) {
            return null // technically this should be impossible
        }
        val jsonUser = content.getObject("user")
        val userId = jsonUser.getUnsignedLong("id")
        val member = guild.getMemberById(userId) as MemberImpl?
        var presence: MemberPresenceImpl? = presences.get(userId)
        val status = OnlineStatus.fromKey(content.getString("status"))
        if (status == OnlineStatus.OFFLINE) {
            presences.remove(userId)
        }
        if (presence == null) {
            presence = MemberPresenceImpl()
            if (status != OnlineStatus.OFFLINE) {
                presences.writeLock().use { _ ->
                    presences.getMap().put(userId, presence)
                }
            }
        }

        // Now that we've update the User's info,
        // lets see if we need to set the specific Presence information.
        // This is stored in the Member objects.
        // We set the activities to null to prevent parsing if the cache was disabled
        val activityArray: DataArray? =
            if (!getJDA().isCacheFlagSet(CacheFlag.ACTIVITY) || content.isNull("activities")) {
                null
            } else {
                content.getArray("activities")
            }
        val newActivities: MutableList<Activity> = ArrayList()
        val parsedActivity = parseActivities(userId, activityArray, newActivities)

        if (getJDA().isCacheFlagSet(CacheFlag.CLIENT_STATUS) && !content.isNull("client_status")) {
            handleClientStatus(content, presence)
        }

        // Check if activities changed
        if (parsedActivity) {
            handleActivities(newActivities, member, presence)
        }

        // The member is already cached, so modify the presence values and fire events as needed.

        if (presence.getOnlineStatus() != status) {
            val oldStatus = presence.getOnlineStatus()
            presence.setOnlineStatus(status)
            if (member != null) {
                getJDA().getEntityBuilder().updateMemberCache(member)
                getJDA().handleEvent(UserUpdateOnlineStatusEvent(getJDA(), responseNumber, member, oldStatus))
            }
        }
        return null
    }

    @Suppress("TooGenericExceptionCaught") // presence JSON is untrusted; any parse failure is logged and skipped
    private fun parseActivities(
        userId: Long,
        activityArray: DataArray?,
        newActivities: MutableList<Activity>,
    ): Boolean {
        var parsedActivity = false
        try {
            if (activityArray != null) {
                for (i in 0 until activityArray.length()) {
                    newActivities.add(EntityBuilder.createActivity(activityArray.getObject(i)))
                }
                parsedActivity = true
            }
        } catch (ex: Exception) {
            if (EntityBuilder.LOG.isDebugEnabled) {
                EntityBuilder.LOG.warn(
                    "Encountered exception trying to parse a presence! UserID: {} JSON: {}",
                    userId,
                    activityArray,
                    ex,
                )
            } else {
                EntityBuilder.LOG.warn(
                    "Encountered exception trying to parse a presence! UserID: {} Message: {} Enable debug for details",
                    userId,
                    ex.message,
                )
            }
        }
        return parsedActivity
    }

    private fun handleActivities(
        newActivities: List<Activity>,
        member: MemberImpl?,
        presence: MemberPresenceImpl,
    ) {
        val oldActivities: List<Activity> = presence.getActivities()
        presence.setActivities(newActivities)
        if (member == null) {
            return
        }
        val unorderedEquals = Helpers.deepEqualsUnordered(oldActivities, newActivities)
        if (unorderedEquals) {
            val deepEquals = Helpers.deepEquals(oldActivities, newActivities)
            if (!deepEquals) {
                getJDA().handleEvent(
                    UserUpdateActivityOrderEvent(getJDA(), responseNumber, oldActivities, member),
                )
            }
        } else {
            getJDA().getEntityBuilder().updateMemberCache(member)
            val stoppedActivities: MutableList<Activity> = ArrayList(oldActivities) // create modifiable copy
            val startedActivities: MutableList<Activity> = ArrayList()
            for (activity in newActivities) {
                if (!stoppedActivities.remove(activity)) {
                    startedActivities.add(activity)
                }
            }

            for (activity in startedActivities) {
                getJDA().handleEvent(UserActivityStartEvent(getJDA(), responseNumber, member, activity))
            }

            for (activity in stoppedActivities) {
                getJDA().handleEvent(UserActivityEndEvent(getJDA(), responseNumber, member, activity))
            }

            getJDA().handleEvent(UserUpdateActivitiesEvent(getJDA(), responseNumber, member, oldActivities))
        }
    }

    private fun handleClientStatus(
        content: DataObject,
        presence: MemberPresenceImpl,
    ) {
        val json = content.getObject("client_status")
        val types = EnumSet.of(ClientType.UNKNOWN)
        for (key in json.keys()) {
            val type = ClientType.fromKey(key)
            types.add(type)
            val raw = json.get(key).toString()
            val clientStatus = OnlineStatus.fromKey(raw)
            presence.setOnlineStatus(type, clientStatus)
        }
        for (type in EnumSet.complementOf(types)) {
            presence.setOnlineStatus(type, null) // set remaining types to offline
        }
    }

    companion object {
        private val log = JDALogger.getLog(PresenceUpdateHandler::class.java)
    }
}
