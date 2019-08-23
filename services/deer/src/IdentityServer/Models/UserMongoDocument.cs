using MongoDB.Bson.Serialization.Attributes;

namespace IdentityServer.Models
{
    [BsonIgnoreExtraElements]
    public class UserMongoDocument
    {
        [BsonId]
        public string Username { get; set; }

        [BsonElement("salt")]
        public byte[] Salt { get; set; }
        
        [BsonElement("hash")]
        public byte[] PasswordHash { get; set; }

        [BsonElement("log_index")]
        public string LogIndexName { get; set; }
        
        [BsonElement("log_exchange")]
        public string LogExchangeName { get; set; }

        [BsonElement("log_queue")]
        public string LogQueueName { get; set; }

        [BsonElement("error_queue")]
        public string ErrorQueueName { get; set; }

        public static UserMongoDocument Create(User user)
        {
            if (user == null)
                return null;

            return new UserMongoDocument
            {
                Username = user.Username,
                Salt = user.Salt,
                PasswordHash = user.PasswordHash,
                LogIndexName = user.LogIndexName,
                LogExchangeName = user.LogExchangeName,
                LogQueueName = user.LogQueueName,
                ErrorQueueName = user.ErrorQueueName
            };
        }

        public User ToUser()
        {
            return new User
            {
                Username = Username,
                Salt = Salt,
                PasswordHash = PasswordHash,
                LogIndexName = LogIndexName,
                LogExchangeName = LogExchangeName,
                LogQueueName = LogQueueName,
                ErrorQueueName = ErrorQueueName
            };
        }
    }
}