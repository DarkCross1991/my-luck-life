CC ?= gcc
CFLAGS ?= -std=c99 -Wall -Wextra -O2 -I../include -D_GNU_SOURCE
LDFLAGS ?= -lm

PORTABLE = ../source/eq.c ../source/formats.c ../source/wavdec.c \
	../source/library.c ../source/viz.c ../source/player.c ../source/decoder.c

.PHONY: all clean run

all: test_eq test_wav test_formats test_library test_player

test_eq: test_eq.c ../source/eq.c
	$(CC) $(CFLAGS) -o $@ test_eq.c ../source/eq.c $(LDFLAGS)

test_wav: test_wav.c ../source/wavdec.c
	$(CC) $(CFLAGS) -o $@ test_wav.c ../source/wavdec.c $(LDFLAGS)

test_formats: test_formats.c ../source/formats.c
	$(CC) $(CFLAGS) -o $@ test_formats.c ../source/formats.c

test_library: test_library.c ../source/library.c ../source/formats.c
	$(CC) $(CFLAGS) -o $@ test_library.c ../source/library.c ../source/formats.c

test_player: test_player.c $(PORTABLE)
	$(CC) $(CFLAGS) -o $@ test_player.c $(PORTABLE) $(LDFLAGS)

run: all
	./test_eq
	./test_wav
	./test_formats
	./test_library
	./test_player

clean:
	rm -f test_eq test_wav test_formats test_library test_player
	rm -rf tmp
