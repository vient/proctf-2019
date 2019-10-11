using System;
using System.Collections.Generic;
using System.Linq;

namespace Uploader.Models
{
    public class Playlist
    {
        public Dictionary<string, string> Tracks { get; }
        public Dictionary<string, AudioFile> AudioFiles { get; }
        private const string M3UINFOHeader = "#EXTINF";

        private Playlist(int size)
        {
            Tracks = new Dictionary<string, string>(size);
            AudioFiles = new Dictionary<string, AudioFile>(size);
        }

        private void AddTrack(string trackName, string trackPath) => 
            Tracks[trackName] = trackPath;

        public override string ToString() => 
            string.Join(", ", Tracks.Select(x => $"{x.Key}: {x.Value}"));


        public static Playlist FromM3U(string m3uContent)
        {
            var m3uLines = m3uContent.Split('\n', StringSplitOptions.RemoveEmptyEntries);
            if (m3uLines.Length < 1)
            {
                throw new ArgumentException("Empty m3u file");
            }

            var tracks = m3uLines
                .Select((line, index) => (index, line))
                .Where(x => x.line.StartsWith(M3UINFOHeader))
                .Select(x => (x.line, m3uLines[x.index + 1]))
                .ToList();

            var playlist = new Playlist(tracks.Count);
            foreach (var (description, link) in tracks)
            {
                var trackName = description.Split(",", StringSplitOptions.RemoveEmptyEntries);
                if (trackName.Length < 2)
                {
                    throw new ArgumentException("Could not find track info");
                }
                
                playlist.AddTrack(trackName[1].Trim(' '), link);
            }

            return playlist;
        }
    }
}