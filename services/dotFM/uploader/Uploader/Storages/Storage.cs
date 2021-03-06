using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Uploader.Models;


namespace Uploader.Storages
{
    public class Storage : IStorage
    {
        private readonly string workingPath;
        private readonly Func<TimeSpan> expirationTime;

        public Storage(string workingPath, Func<TimeSpan> expirationTime)
        {
            this.workingPath = workingPath;
            this.expirationTime = expirationTime;
            
            new TaskFactory().StartNew(async () =>
            {
                while (true)
                {
                    try
                    {
                        Cleanup();
                    }
                    finally
                    {
                        await Task.Delay(TimeSpan.FromSeconds(30));
                    }
                }
            });
        }

        private void CreateTracks(Playlist playlist)
        {
            foreach (var (key, value) in playlist.TrackFiles)
            {
                using (var musFs = CreateFileStream(Path.Combine(Constants.MusicPath, key + ".mp3")))
                    musFs.Write(value.GetContent());

                using (var imgFs = CreateFileStream(Path.Combine(Constants.ImagesPaths, value + ".png")))
                    imgFs.Write(value.GetImage());
            }
        }

        private Guid CreatePlaylistFile(Playlist playlist)
        {
            var playlistId = Guid.NewGuid();
            using var streamWriter = File.CreateText(Path.Combine(Constants.PlaylistsPath, playlistId + ".m3u"));
            streamWriter.Write(playlist.ToString().AsSpan());
            return playlistId;
        }

        private Stream CreateFileStream(string path) => new FileStream(Path.Join(workingPath, path), FileMode.Create);

        public Guid Store(Playlist playlist)
        {
            CreateDirsIfNotExists();
            CreateTracks(playlist);
            return CreatePlaylistFile(playlist);
        }

        private static void CreateDirsIfNotExists()
        {
            Utils.CreateDirectoryIfNotExists(Constants.RootPath);
            Utils.CreateDirectoryIfNotExists(Constants.PlaylistsPath);
            Utils.CreateDirectoryIfNotExists(Constants.MusicPath);
            Utils.CreateDirectoryIfNotExists(Constants.ImagesPaths);
        }

        public List<string> Get(Guid guid)
        {
            string file;
            try
            {
                file = Directory
                    .GetFiles(Constants.PlaylistsPath).Select(x => x.Split("/")[^1])
                    .First(x =>
                    {
                        Guid.TryParse(x.Split(".").First(), out var parsedGuid);
                        return parsedGuid == guid;
                    });
            }
            catch (InvalidOperationException)
            {
                return null;
            }
            
            using var fs = new FileStream(Path.Combine(Constants.PlaylistsPath, file), FileMode.Open);
            var fileBytes = new byte[fs.Length];
            fs.Read(new Span<byte>(fileBytes));
            var ps = Playlist.FromM3U(Encoding.UTF8.GetString(fileBytes));
            return ps.TrackPaths.Select(x => x.Value).ToList();
        }

        private void Cleanup()
        {
            var filesForCleanup = Directory
                .GetFiles(Constants.RootPath, "*.mp3", SearchOption.AllDirectories)
                .Where(x => File.GetCreationTime(x) + expirationTime() < DateTime.Now).ToList();

            foreach (var file in filesForCleanup) File.Delete(file);
        }
    }
}