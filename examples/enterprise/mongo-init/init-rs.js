// Initialize MongoDB replica set for change stream support.
// This script runs once when the container starts with --replSet rs0.
rs.initiate({
  _id: "rs0",
  members: [{ _id: 0, host: "mongodb:27017" }]
});
