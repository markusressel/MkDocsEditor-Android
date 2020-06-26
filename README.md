<p align="center" style="font-size: xx-large;">
  <img alt="Logo" src="app/src/main/ic_launcher-web.png" width="144"/> </br>
</p>

# MkDocsEditor

A realtime collaborative editor for MkDocs projects.

# :warning: Work In Progress

This project is still a work in progress and is **not yet ready for production**.

# Build Status

| Master | Dev |
|--------|-----|
| [![Master](https://travis-ci.org/MkDocsEditor/MkDocsEditor-Android.svg?branch=master)](https://travis-ci.org/MkDocsEditor/MkDocsEditor-Android/branches) | [![Master](https://travis-ci.org/MkDocsEditor-Android/MkDocsEditor-Android.svg?branch=dev)](https://travis-ci.org/MkDocsEditor-Android/MkDocsEditor-Android/branches) |
| [![codebeat badge](https://codebeat.co/badges/606cd0dd-3e92-4639-904a-9ad5015a5cd3)](https://codebeat.co/projects/github-com-markusressel-MkDocsEditor-Android-master) | [![codebeat badge](https://codebeat.co/badges/e4ee51d2-fbe8-428f-95af-44488d8b44e6)](https://codebeat.co/projects/github-com-markusressel-MkDocsEditor-Android-dev) |

# What is this?

MkDocsEditor aims to provide an easy way for you to manage your existing [MkDocs](https://www.mkdocs.org/) projects:
* [ ] Subsection management (folders)
  * [ ] create new sections
  * [ ] rename existing sections
  * [ ] move an entire section to another location
  * [ ] delete a section and all the content inside it
* [ ] Document management (`*.md` files)
  * [ ] create new documents
  * [ ] rename existing documents
  * [x] edit documents
  * [ ] see the result on your hosted website right away
  * [ ] move a document (and all resources that are referenced in it) to another location
  * [ ] delete documents (and all resources that are referenced in it)
* [ ] Resource file management (any file that is not a markdown file really)
  * [ ] upload new resource files to use in a document
  * [ ] download and open linked resources (if supported by another app on your device)
  * [ ] delete resource files
    * [ ] automatically replace any reference to this resource in a document with a "deleted" note

## Realtime collaborative editing

MkDocsEditor is built around the famous [Differential Synchronization](https://neil.fraser.name/writing/sync/)
algorithm developed by [Neil Fraser](https://neil.fraser.name/) which allows
you to edit a document simultaniously while someone else is also editing.

**TODO: insert gif here**

# How to use

## Server setup

To use this app an instance of [MkDocsEditor-Backend](https://github.com/MkDocsEditor/MkDocsEditor-Backend) needs to run on the server
where your [MkDocs](https://www.mkdocs.org/) project source files are located.
Have a look at the documentation on it's [project site](https://github.com/MkDocsEditor/MkDocsEditor-Backend) to learn more
on how to do this.

## Client Setup

Simply enter the server connection details in the settings of the app
and voila your done! :smile:

# Developer Instructions

## How to build

Building this project should be straight forward. Just check out the
repo and let Android Studio handle the rest.

## Gradle
Since this project uses a Gradle version >= 4.6 and the Android Gradle Plugin
only supports "configuration on demand" on lower versions you have to
manually disable it in your IntelliJ/Android Studio project settings.
See this page for more info: https://stackoverflow.com/a/49994951/1941623


# Contributing
GitHub is for social coding: if you want to write code, I encourage
contributions through pull requests from forks of this repository.
Create GitHub tickets for bugs and new features and comment on the ones
that you are interested in.

# License

**AGPLv3+**
