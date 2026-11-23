-- The V5 seed shipped one image file name with a space ("quantum Commons.png"). The file is now
-- quantumCommons.png like the others; V5 stays untouched so its checksum still validates.
UPDATE station
SET image_url = '/api/v1/stops-images/quantumCommons.png'
WHERE image_url = '/api/v1/stops-images/quantum%20Commons.png';
