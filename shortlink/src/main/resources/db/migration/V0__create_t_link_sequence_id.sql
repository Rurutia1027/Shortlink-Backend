-- Create a custom sequence for IDs
CREATE SEQUENCE t_link_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;