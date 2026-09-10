Amazon S3 Storage Plugin for Fess
[![Java CI with Maven](https://github.com/codelibs/fess-storage-s3/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-storage-s3/actions/workflows/maven.yml)
=================================

Amazon S3 and S3-compatible storage support for [Fess](https://github.com/codelibs/fess).

This plugin provides two things:

* the **storage client** behind `storage.type=s3` and `storage.type=s3_compat`, which backs the
  Storage page of the administration screen and its REST API — the only callers of
  `StorageClientFactory` in Fess
* the **crawler client** behind `s3:` URLs, so that a file crawling configuration can point at a
  bucket

Both were part of the Fess distribution until 15.9. They moved here with the AWS SDK for Java,
which is about 8 MiB of jars that most installations never use.

## Installation

```
$ bin/fess-setup install plugin fess-storage-s3
```

Or download the jar from [maven.codelibs.org](https://maven.codelibs.org/org/codelibs/fess/fess-storage-s3/)
and put it in `app/WEB-INF/plugin`. Restart Fess afterwards: the components this plugin
contributes are read when the DI container is built.

## Configuration

### Object storage

Set these in the admin general page, or pass them to the JVM as
`-Dfess.system.storage.type=...`:

| Key | Value |
| --- | --- |
| `storage.type` | `s3`, `s3_compat`, or `auto` (the default), which picks one from the endpoint |
| `storage.bucket` | the bucket name |
| `storage.endpoint` | a custom endpoint, for MinIO and the like (blank for Amazon S3) |
| `storage.accesskey` | the access key |
| `storage.secretkey` | the secret key |
| `storage.region` | the region (default `us-east-1`) |

`auto` reads `storage.endpoint`: a blank endpoint or an Amazon one resolves to `s3`, anything
else to `s3_compat`. Both are served by the same client, so the distinction only matters when
`storage.type` is set by hand.

### Crawling a bucket

Create a file crawling configuration whose path is `s3://<bucket>/<prefix>`. The plugin adds
`s3` to the crawler's file protocol list on startup, so `crawler.file.protocols` needs no edit.

The crawler client takes its own `endpoint`, `accessKey`, `secretKey` and `region` from the
configuration parameters. With `accessKey` and `secretKey` left out, it falls back to the AWS
SDK default credential provider chain, so an instance profile or IRSA works as usual.

## Version

| Fess | Plugin |
| --- | --- |
| 15.9.x | 15.9.x |

Match the minor version. Installing this plugin into Fess 15.8 or earlier stops every crawl:
those versions register `s3Client` in fess-crawler's own `crawler/client.xml`, and a second
registration from this plugin makes `clientFactory` fail to build with
`TooManyRegistrationComponentException` — not only for `s3:` URLs, but for all of them.

## How it plugs in

Nothing here is wired by class name from Fess. The plugin ships two additive LastaDi files that
Fess merges from every jar on the class path:

* `fess_storage++.xml` registers `s3StorageClient` and `s3_compatStorageClient`.
  `StorageClientFactory` resolves a client as `<storage.type>StorageClient`, so the component
  names are what make `storage.type=s3` and the `S3_COMPAT` auto-detection result resolve.
* `crawler/client++.xml` registers `s3Client` and calls `crawlerClientCreator.register()` for
  `s3:.*`, the same way fess-crawler-playwright registers its client.

`S3Client` and the `s3:` URL handler themselves remain in fess-crawler; only the SDK they compile
against ships here. The SDK is shaded in without relocation for that reason — those classes resolve
`software.amazon.awssdk` by its real name.
