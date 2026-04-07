-- Add AI validation fields to complaints table
ALTER TABLE complaints 
ADD COLUMN ai_validation_score DOUBLE,
ADD COLUMN ai_validation_reason TEXT,
ADD COLUMN ai_validated_at TIMESTAMP,
ADD COLUMN ai_validation_passed BOOLEAN,
ADD COLUMN rejection_reason TEXT;

-- Create indexes for better performance
CREATE INDEX idx_complaints_ai_validated_at ON complaints(ai_validated_at);
CREATE INDEX idx_complaints_ai_validation_passed ON complaints(ai_validation_passed);
CREATE INDEX idx_complaints_ai_validation_score ON complaints(ai_validation_score);
