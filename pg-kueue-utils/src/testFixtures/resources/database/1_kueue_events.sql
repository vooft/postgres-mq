CREATE TABLE kueue_events (
    id UUID PRIMARY KEY,
    topic TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX kueue_events_topic_created_at_idx ON kueue_events (topic, created_at);
