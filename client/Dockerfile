# ---- build stage ----
FROM node:20-alpine AS build

WORKDIR /app

# Vite inlines import.meta.env.VITE_* into the JS bundle at BUILD time. The API base therefore
# has to be supplied here, as a build argument, and not as a runtime environment variable on
# the nginx container below -- by the time that container starts, the value is already baked
# into the shipped JavaScript and setting the variable would have no effect at all.
#
# This is the URL the BROWSER calls, so it must be the API's host-published address. The
# compose-internal name (http://api:3000) would not resolve: the browser is not on the
# compose network.
ARG VITE_API_BASE=http://localhost:3000/api
ENV VITE_API_BASE=$VITE_API_BASE

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- serve stage ----
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
