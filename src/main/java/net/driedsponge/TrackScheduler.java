package net.driedsponge;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.driedsponge.buttons.SkipButton;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private final BlockingQueue<Song> queue;
    public VoiceController vc;
    public static final int QUEUE_LIMIT = 500;

    /**
     * Creates a new instance of a TrackScheduler.
     * @param vc The voice controller in control.
     */
    public TrackScheduler(VoiceController vc) {
        this.vc = vc;
        this.queue = new LinkedBlockingQueue<>();
    }

    @Override
    public void onPlayerPause(AudioPlayer player) {
        // Player was paused
    }

    @Override
    public void onPlayerResume(AudioPlayer player) {
        // Player was resumed
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            startNewTrack((Member) null);
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {

    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {

    }

    /**
     * Get the current queue.
     * @return The queue
     */
    public BlockingQueue<Song> getQueue() {
        return queue;
    }

    /**
     * Shuffle the queue.
     * @return Whether the queue was successfully shuffled or not.
     */
    public boolean shuffle() {
        if (this.getQueue().size() > 0) {
            List<Object> songs = Arrays.asList(this.queue.toArray());
            Collections.shuffle(songs,new Random());
            this.queue.clear();
            this.queue.addAll((Collection) songs);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Queue a song.
     * @param song The song to queue,
     * @param notify Whether to notify that the song was queued.
     */
    public void queue(Song song, boolean notify) {
        if (!vc.getPlayer().startTrack(song.getTrack(), true)) {
            queue.offer(song);
            if(notify){
                song.getEvent().getHook().sendMessageEmbeds(songCard("Song Added to Queue", song).build()).queue();
            }
        }else{
            song.getEvent().getHook().sendMessageEmbeds(TrackScheduler.songCard("Now Playing",song).build())
                    .addActionRow(SkipButton.SKIP_BUTTON)
                    .queue();
            vc.setNowPlaying(song);
        }
    }

    /**
     * Queue a playlist.
     * @param playlist The AudioPlaylist to add.
     * @param event The event associated with the playlist.
     */
    public void queue(AudioPlaylist playlist, SlashCommandInteractionEvent event) {
        int playListSize = playlist.getTracks().size();

        int loopLimit = Math.min(playListSize, QUEUE_LIMIT);

        for (int i = 0; i < loopLimit; i++) {
            AudioTrack track = playlist.getTracks().get(i);
            Song song = new Song(track, event);
            if (!vc.getPlayer().startTrack(song.getTrack(), true)) {
                queue.offer(song);
            } else {
                vc.setNowPlaying(song);
                vc.getTextChannel().sendMessageEmbeds(songCard("Now Playing", song).build())
                        .addActionRow(SkipButton.SKIP_BUTTON)
                        .queue();
            }
        }
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Added " + playlist.getTracks().size() + " songs to the Queue from " + playlist.getName() + "!");
        embedBuilder.setColor(Main.PRIMARY_COLOR);
        embedBuilder.setFooter("Requested by " + event.getUser().getAsTag(), event.getUser().getEffectiveAvatarUrl());

        event.getHook().sendMessageEmbeds(embedBuilder.build())
                .addActionRow(Button.link(event.getOptions().get(0).getAsString(), "Playlist"))
                .queue();
    }

    /**
     * Generates an embed perfect for sharing songs.
     * @param title The title of the card.
     * @param song The song.
     * @return A build for the song embed.
     */
    public static EmbedBuilder songCard(String title, Song song) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setAuthor(title);
        embedBuilder.setTitle(song.getInfo().title, song.getInfo().uri);
        embedBuilder.setThumbnail(song.getThumbnail());
        embedBuilder.addField("Artist", song.getInfo().author, true);
        embedBuilder.addField("Length", duration(song.getTrack().getDuration()), true);
        embedBuilder.setColor(Main.PRIMARY_COLOR);
        embedBuilder.setFooter("Requested by " + song.getRequester().getUser().getAsTag(), song.getRequester().getEffectiveAvatarUrl());
        if(song.getThumbnail() != null){
            embedBuilder.setThumbnail(song.getThumbnail());
        }
        return embedBuilder;
    }

    /**
     * Convert milliseconds to MM:SS/np
     * @param milliseconds
     * @return
     */
    public static String duration(long milliseconds){
        // Define the number of milliseconds
        Duration duration = Duration.ofMillis(milliseconds);
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds() % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);
        return timeString;
    }

    /**
     * Starts a new track taken from the queue.
     * @param member Option Param for who skipped the last track
     */
    public void startNewTrack(@Nullable Member member) {
        String lastSong = vc.getNowPlaying().getInfo().title;
        if (queue.isEmpty()) {
            Guild guild = vc.getGuild();
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setColor(Main.PRIMARY_COLOR)
                    .setTitle(String.format(":wave: No more songs to play. Leaving %s!",vc.getVoiceChannel().getName()));

            if(member != null){
                vc.getTextChannel().sendMessage(":fast_forward: "+member.getAsMention()+" skipped **"+lastSong+"**")
                        .addEmbeds(embedBuilder.build()).queue();
            }else{
                vc.getTextChannel().sendMessageEmbeds(embedBuilder.build()).queue();
            }
            vc.leave();
            PlayerStore.remove(guild);
            return;
        }
        Song song = queue.poll();
        this.vc.setNowPlaying(song);
        vc.getPlayer().playTrack(song.getTrack());
        MessageCreateAction msg = vc.getTextChannel().sendMessageEmbeds(songCard("Now Playing", song).build());
        if(member != null){
            msg = vc.getTextChannel()
                    .sendMessage(":fast_forward: "+member.getAsMention()+" skipped **"+lastSong+"**")
                    .addEmbeds(songCard("Now Playing", song).build());
        }
        msg.addActionRow(SkipButton.SKIP_BUTTON);
        msg.queue();
    }
    /**
     * Starts a new specified track.
     * @param song The song to start.
     */
    public void startNewTrack(Song song) {
            this.vc.setNowPlaying(song);
            vc.getPlayer().playTrack(song.getTrack());
            vc.getTextChannel().sendMessageEmbeds(songCard("Now Playing", song).build())
                    .addActionRow(SkipButton.SKIP_BUTTON)
                    .queue();
    }
}
