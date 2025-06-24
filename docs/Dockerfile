FROM ruby:3.3

# Set gem installation to avoid documentation for faster builds
ENV GEM_HOME=/usr/local/bundle
ENV BUNDLE_SILENCE_ROOT_WARNING=1 BUNDLE_JOBS=4

# Install Jekyll
RUN gem install jekyll -v 4.3.4 --no-document && \
    gem cleanup

# Set working directory
WORKDIR /site

# Expose default Jekyll port
EXPOSE 4000

# Default command to serve Jekyll site
CMD ["jekyll", "serve", "--host", "0.0.0.0"]