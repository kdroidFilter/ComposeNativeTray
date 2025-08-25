tag-changelog: require-version require-gh-token
	echo "Tagging..." && \
	git tag -a "$$VERSION" -f --annotate -m"Tagged $$VERSION" && \
	git push --tags -f && \
	git checkout master && \
	git pull && \
	github_changelog_generator --no-issues --max-issues 100 --token "${GH_TOKEN}" --user getlantern --project systray && \
	git add CHANGELOG.md && \
	git commit -m "Updated changelog for $$VERSION" && \
	git push origin HEAD && \
	git checkout -

guard-%:
	 @ if [ -z '${${*}}' ]; then echo 'Environment variable $* not set' && exit 1; fi

require-version: guard-VERSION

require-gh-token: guard-GH_TOKEN

.PHONY: build-so
build-so:
	GOOS=linux GOARCH=$${GOARCH:-amd64} CGO_ENABLED=1 \
	CGO_CFLAGS="-Os -ffunction-sections -fdata-sections -fPIC" \
 CGO_LDFLAGS="-Wl,-O1 -Wl,--as-needed -Wl,--gc-sections -Wl,-s" \
 go build -buildmode=c-shared -trimpath -gcflags=all=-l -ldflags "-s -w -buildid= -linkmode=external -extldflags '-Wl,-O1 -Wl,--as-needed -Wl,-s -Wl,--gc-sections'" -o dist/libsystray.so ./jna
	strip --strip-unneeded -R .comment -R .note.GNU-stack -R .note.gnu.property -R .note.go.buildid dist/libsystray.so || true
	@echo "Built dist/libsystray.so and dist/libsystray.h (extra size-optimized)"
